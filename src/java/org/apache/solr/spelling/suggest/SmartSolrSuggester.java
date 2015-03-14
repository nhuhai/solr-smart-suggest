package org.apache.solr.spelling.suggest;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.IOUtils;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CloseHook;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.lucene.search.suggest.analyzing.SmartAnalyzingSuggester;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.SortedIntDocSet;
import org.apache.solr.search.BitDocSet;
import java.util.Comparator;
import org.apache.lucene.util.BytesRef;
import java.util.Collections;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.index.Term;
import java.util.Iterator;

/** 
 * Responsible for loading the lookup and dictionary Implementations specified by 
 * the SolrConfig. 
 * Interacts (query/build/reload) with Lucene Suggesters through {@link Lookup} and
 * {@link Dictionary}
 * */
public class SmartSolrSuggester implements Accountable {
  private static final Logger LOG = LoggerFactory.getLogger(SmartSolrSuggester.class);
  
  /** Name used when an unnamed suggester config is passed */
  public static final String DEFAULT_DICT_NAME = "default";
  
  /** Label to identify the name of the suggester */
  public static final String NAME = "name";
  
  /** Location of the source data - either a path to a file, or null for the
   * current IndexReader.
   * */
  public static final String LOCATION = "sourceLocation";
  
  /** Fully-qualified class of the {@link Lookup} implementation. */
  public static final String LOOKUP_IMPL = "lookupImpl";
  
  /** Fully-qualified class of the {@link Dictionary} implementation */
  public static final String DICTIONARY_IMPL = "dictionaryImpl";
  
  /**
   * Name of the location where to persist the dictionary. If this location
   * is relative then the data will be stored under the core's dataDir. If this
   * is null the storing will be disabled.
   */
  public static final String STORE_DIR = "storeDir";
  
  static SuggesterResult EMPTY_RESULT = new SuggesterResult();
  
  private String sourceLocation;
  private File storeDir;
  private Dictionary dictionary;
  private Lookup lookup;
  private String lookupImpl;
  private String dictionaryImpl;
  private String name;

  private LookupFactory factory;
  private DictionaryFactory dictionaryFactory;

  private SolrIndexSearcher solrIndexSearcher;

  public void setSolrIndexSearcher(SolrIndexSearcher solrIndexSearcher) {
    System.out.println("setSolrIndexSearcher()");
    this.solrIndexSearcher = solrIndexSearcher;
  }
  
  /** 
   * Uses the <code>config</code> and the <code>core</code> to initialize the underlying 
   * Lucene suggester
   * */
  public String init(NamedList<?> config, SolrCore core) {
    LOG.info("init: " + config);

    // read the config
    name = config.get(NAME) != null ? (String) config.get(NAME)
        : DEFAULT_DICT_NAME;
    sourceLocation = (String) config.get(LOCATION);
    lookupImpl = (String) config.get(LOOKUP_IMPL);
    dictionaryImpl = (String) config.get(DICTIONARY_IMPL);
    String store = (String)config.get(STORE_DIR);

    if (lookupImpl == null) {
      lookupImpl = LookupFactory.DEFAULT_FILE_BASED_DICT;
      LOG.info("No " + LOOKUP_IMPL + " parameter was provided falling back to " + lookupImpl);
    }
    // initialize appropriate lookup instance
    factory = core.getResourceLoader().newInstance(lookupImpl, LookupFactory.class);
    lookup = factory.create(config, core);
    core.addCloseHook(new CloseHook() {
      @Override
      public void preClose(SolrCore core) {
        if (lookup != null && lookup instanceof Closeable) {
          try {
            ((Closeable) lookup).close();
          } catch (IOException e) {
            LOG.warn("Could not close the suggester lookup.", e);
          }
        }
      }
      
      @Override
      public void postClose(SolrCore core) {}
    });

    // if store directory is provided make it or load up the lookup with its content
    if (store != null) {
      storeDir = new File(store);
      if (!storeDir.isAbsolute()) {
        storeDir = new File(core.getDataDir() + File.separator + storeDir);
      }
      if (!storeDir.exists()) {
        storeDir.mkdirs();
      } else {
        // attempt reload of the stored lookup
        try {
          lookup.load(new FileInputStream(new File(storeDir, factory.storeFileName())));
        } catch (IOException e) {
          LOG.warn("Loading stored lookup data failed, possibly not cached yet");
        }
      }
    }
    
    // dictionary configuration
    if (dictionaryImpl == null) {
      dictionaryImpl = (sourceLocation == null) ? DictionaryFactory.DEFAULT_INDEX_BASED_DICT : 
        DictionaryFactory.DEFAULT_FILE_BASED_DICT;
      LOG.info("No " + DICTIONARY_IMPL + " parameter was provided falling back to " + dictionaryImpl);
    }
    
    dictionaryFactory = core.getResourceLoader().newInstance(dictionaryImpl, DictionaryFactory.class);
    dictionaryFactory.setParams(config);
    LOG.info("Dictionary loaded with params: " + config);

    return name;
  }

  /** Build the underlying Lucene Suggester */
  public void build(SolrCore core, SolrIndexSearcher searcher) throws IOException {
    LOG.info("build()");

    dictionary = dictionaryFactory.create(core, searcher);
    lookup.build(dictionary);
    if (storeDir != null) {
      File target = new File(storeDir, factory.storeFileName());
      if(!lookup.store(new FileOutputStream(target))) {
        LOG.error("Store Lookup build failed");
      } else {
        LOG.info("Stored suggest data to: " + target.getAbsolutePath());
      }
    }
  }

  /** Reloads the underlying Lucene Suggester */
  public void reload(SolrCore core, SolrIndexSearcher searcher) throws IOException {
    LOG.info("reload()");
    if (dictionary == null && storeDir != null) {
      // this may be a firstSearcher event, try loading it
      FileInputStream is = new FileInputStream(new File(storeDir, factory.storeFileName()));
      try {
        if (lookup.load(is)) {
          return;  // loaded ok
        }
      } finally {
        IOUtils.closeWhileHandlingException(is);
      }
      LOG.debug("load failed, need to build Lookup again");
    }
    // loading was unsuccessful - build it again
    build(core, searcher);
  }

  /** Returns suggestions based on the {@link SuggesterOptions} passed */
  public SuggesterResult getSuggestions(ContextSuggesterOptions options) throws IOException {
    //LOG.debug("getSuggestions: " + options.token);
    if (lookup == null) {
      LOG.info("Lookup is null - invoke suggest.build first");
      return EMPTY_RESULT;
    }
    
    SuggesterResult res = new SuggesterResult();
    List<LookupResult> suggestions = ((SmartAnalyzingSuggester)lookup).lookup(options.token, null, false, options.count);

    // Sort resutls
    ScoreComparator scoreComparator = new ScoreComparator(this.solrIndexSearcher, options.context);
    Collections.sort(suggestions, scoreComparator);

    res.add(getName(), options.token.toString(), suggestions);
    return res;
  }


  private static class ScoreComparator implements Comparator<LookupResult> {
    private final SolrIndexSearcher searcher;
    private final String context;
    private SortedIntDocSet contextDocIdSet;

    public ScoreComparator(SolrIndexSearcher searcher, String context) throws IOException {
      this.searcher = searcher;
      this.context = context;
      this.contextDocIdSet = this.getContextSortedDocIdSet();
    }

    @Override
    public int compare(LookupResult left, LookupResult right) {
      int leftScore = 0;
      int rightScore = 0; 

      try {
        leftScore += this.getScore(left.payload);
        rightScore += this.getScore(right.payload);
      } catch(IOException e) {
        System.out.println("IOException");
      }

      // System.out.println("left = " + leftScore + " - right = " + rightScore);

      if (leftScore > rightScore) {
        return -1;
      } else if (leftScore == rightScore) {
        return 0;
      } else {
        return 1;
      }
    }

    public SortedIntDocSet getContextSortedDocIdSet() throws IOException {
      Query query = new TermQuery(new Term("text", this.context.toLowerCase()));
      DocSet tempDocSet = searcher.getDocSet(query);
      
      int[] tempDocIdsArray = new int[0];
      
      if (tempDocSet instanceof BitDocSet) {
        BitDocSet bitDocSet = (BitDocSet)tempDocSet;
        int numDocs = bitDocSet.size();
        tempDocIdsArray = new int[numDocs];
        Iterator iter = bitDocSet.iterator();
        int curIdx = 0;
        
        while (iter.hasNext() && curIdx < numDocs) {
          tempDocIdsArray[curIdx++] = (Integer)iter.next();
        }
      }

      if (tempDocSet instanceof SortedIntDocSet) {
        System.out.print("!!!!!! It's a SortedIntDocSet with length = ");
        System.out.println(((SortedIntDocSet)tempDocSet).getDocs().length);
        return (SortedIntDocSet)tempDocSet;
      }

      return new SortedIntDocSet(tempDocIdsArray);
    }

    public int getScore(BytesRef payload) throws IOException {
      byte[] payloadBytes = payload.bytes;
      int[] tempDocIdsArray = new int[(int)(payloadBytes.length/4)];
      int score = 0;

      
      for (int i = 0; i < payloadBytes.length; i+=4) {
        int finalInt= (payloadBytes[i]<<24)&0xff000000|
               (payloadBytes[i+1]<<16)&0x00ff0000|
               (payloadBytes[i+2]<< 8)&0x0000ff00|
               (payloadBytes[i+3]<< 0)&0x000000ff;

        tempDocIdsArray[i] = finalInt;
      }

      SortedIntDocSet curSortedIntDocSet = new SortedIntDocSet(tempDocIdsArray);

      if (this.contextDocIdSet != null) {
        int overlappedScore = curSortedIntDocSet.intersectionSize(this.contextDocIdSet);
        score += overlappedScore;
      }
      
      return score;
    }
  }

  /** Returns the unique name of the suggester */
  public String getName() {
    return name;
  }

  @Override
  public long ramBytesUsed() {
    return lookup.ramBytesUsed();
  }
  
  @Override
  public String toString() {
    return "SmartSolrSuggester [ name=" + name + ", "
        + "sourceLocation=" + sourceLocation + ", "
        + "storeDir=" + ((storeDir == null) ? "" : storeDir.getAbsoluteFile()) + ", "
        + "lookupImpl=" + lookupImpl + ", "
        + "dictionaryImpl=" + dictionaryImpl + ", "
        + "sizeInBytes=" + ((lookup!=null) ? String.valueOf(ramBytesUsed()) : "0") + " ]";
  }

}
