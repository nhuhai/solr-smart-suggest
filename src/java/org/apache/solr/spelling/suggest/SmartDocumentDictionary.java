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
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.spell.Dictionary;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.index.Term;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.lucene.search.suggest.InputIterator;
import java.util.Arrays;
import org.apache.solr.search.SortedIntDocSet;
import org.apache.solr.search.BitDocSet;
import java.util.Iterator;

/**
 * <p>
 * Dictionary with terms, weights, payload (optional) and contexts (optional)
 * information taken from stored/indexed fields in a Lucene index.
 * </p>
 * <b>NOTE:</b> 
 *  <ul>
 *    <li>
 *      The term and (optionally) payload fields have to be
 *      stored
 *    </li>
 *    <li>
 *      The weight field can be stored or can be a {@link NumericDocValues}.
 *      If the weight field is not defined, the value of the weight is <code>0</code>
 *    </li>
 *    <li>
 *      if any of the term or (optionally) payload fields supplied
 *      do not have a value for a document, then the document is 
 *      skipped by the dictionary
 *    </li>
 *  </ul>
 */
public class SmartDocumentDictionary implements Dictionary {
  
  /** {@link IndexReader} to load documents from */
  protected final IndexReader reader;
  protected final SolrIndexSearcher searcher;

  /** Field to read payload from */
  protected final String payloadField;
  /** Field to read contexts from */
  protected final String contextsField;
  private final String field;
  private final String weightField;
  
  /**
   * Creates a new dictionary with the contents of the fields named <code>field</code>
   * for the terms and <code>weightField</code> for the weights that will be used for
   * the corresponding terms.
   */
  public SmartDocumentDictionary(SolrIndexSearcher searcher, String field, String weightField) {
    this(searcher, field, weightField, null);
  }
  
  /**
   * Creates a new dictionary with the contents of the fields named <code>field</code>
   * for the terms, <code>weightField</code> for the weights that will be used for the 
   * the corresponding terms and <code>payloadField</code> for the corresponding payloads
   * for the entry.
   */
  public SmartDocumentDictionary(SolrIndexSearcher searcher, String field, String weightField, String payloadField) {
    this(searcher, field, weightField, payloadField, null);
  }

  /**
   * Creates a new dictionary with the contents of the fields named <code>field</code>
   * for the terms, <code>weightField</code> for the weights that will be used for the 
   * the corresponding terms, <code>payloadField</code> for the corresponding payloads
   * for the entry and <code>contextsFeild</code> for associated contexts.
   */
  public SmartDocumentDictionary(SolrIndexSearcher searcher, String field, String weightField, String payloadField, String contextsField) {
    this.searcher = searcher;
    this.reader = this.searcher.getIndexReader();
    this.field = field;
    this.weightField = weightField;
    this.payloadField = payloadField;
    this.contextsField = contextsField;
  }

  @Override
  public InputIterator getEntryIterator() throws IOException {
    return new DocumentInputIterator(payloadField!=null, contextsField!=null);
  }

  /** Implements {@link InputIterator} from stored fields. */
  public class DocumentInputIterator implements InputIterator {

    private final int docCount;
    private final Set<String> relevantFields;
    private final boolean hasPayloads;
    private final boolean hasContexts;
    private final Bits liveDocs;
    private int currentDocId = -1;
    private long currentWeight;
    private BytesRef currentPayload;
    private Set<BytesRef> currentContexts;
    private final NumericDocValues weightValues;

    private int[] currentDocIdsArray;

    
    /**
     * Creates an iterator over term, weight and payload fields from the lucene
     * index. setting <code>withPayload</code> to false, implies an iterator
     * over only term and weight.
     */
    public DocumentInputIterator(boolean hasPayloads, boolean hasContexts) throws IOException {
      this.hasPayloads = hasPayloads;
      this.hasContexts = hasContexts;
      docCount = reader.maxDoc() - 1;
      weightValues = (weightField != null) ? MultiDocValues.getNumericValues(reader, weightField) : null;
      liveDocs = (reader.leaves().size() > 0) ? MultiFields.getLiveDocs(reader) : null;
      relevantFields = getRelevantFields(new String [] {field, weightField, payloadField, contextsField /*,"uri", "relatedDocs"*/});
    }

    @Override
    public long weight() {
      return currentWeight;
    }

    @Override
    public Comparator<BytesRef> getComparator() {
      return null;
    }

    @Override
    public BytesRef next() throws IOException {
      while (currentDocId < docCount) {
        currentDocId++;
        if (liveDocs != null && !liveDocs.get(currentDocId)) { 
          continue;
        }

        Document doc = reader.document(currentDocId, relevantFields);

        BytesRef tempPayload = null;
        BytesRef tempTerm = null;
        Set<BytesRef> tempContexts = new HashSet<>();

        DocSet tempDocSet = null;
        int[] tempDocIdsArray = null;

        if (hasPayloads) {
          IndexableField payload = doc.getField(payloadField);
          if (payload == null || (payload.binaryValue() == null && payload.stringValue() == null)) {
            continue;
          }
          tempPayload = (payload.binaryValue() != null) ? payload.binaryValue() : new BytesRef(payload.stringValue());
        }

        if (hasContexts) {
          final IndexableField[] contextFields = doc.getFields(contextsField);
          for (IndexableField contextField : contextFields) {
            if (contextField.binaryValue() == null && contextField.stringValue() == null) {
              continue;
            } else {
              tempContexts.add((contextField.binaryValue() != null) ? contextField.binaryValue() : new BytesRef(contextField.stringValue()));
            }
          }
        }

        IndexableField fieldVal = doc.getField(field);
        if (fieldVal == null || (fieldVal.binaryValue() == null && fieldVal.stringValue() == null)) {
          continue;
        }

        if (fieldVal.stringValue() != null) {
          // System.out.println(">>> FIELD STRING VALUE: " + fieldVal.stringValue());
          // Query query = new TermQuery(new Term("text", fieldVal.stringValue().toLowerCase()));

          // if (tempDocSet instanceof SortedIntDocSet) {
          //   System.out.print("It's a SortedIntDocSet with length = ");
          //   System.out.println(((SortedIntDocSet)tempDocSet).getDocs().length);
          //   tempDocIdsArray = ((SortedIntDocSet)tempDocSet).getDocs();
          // }
          tempDocIdsArray = this.getRelatedDocIds(doc);
        } else {
          System.out.println(">>> FIELD BINARY VALUE: " + fieldVal.binaryValue());;
        }

        tempTerm = (fieldVal.stringValue() != null) ? new BytesRef(fieldVal.stringValue()) : fieldVal.binaryValue();

        currentPayload = tempPayload;
        currentContexts = tempContexts;
        currentWeight = getWeight(doc, currentDocId);

        currentDocIdsArray = tempDocIdsArray;
        // System.out.print("currentDocIdsArray = ");
        // System.out.println(Arrays.toString(currentDocIdsArray));

        return tempTerm;
      }
      return null;
    }

    public int[] getRelatedDocIds(Document doc) throws IOException {
      String uri = doc.get("uri");
      System.out.println(">>> URI = " + uri);
      String[] relatedDocs = doc.getValues("relatedDocs");
      // System.out.println("relatedDocs = " + Arrays.toString(relatedDocs));

      int count = 0;

      if (uri == null) {
        return new int[0];
      } else {
        count += 1;

        if (relatedDocs.length > 0) {
          count += relatedDocs.length;
        }

        int[] docIds = new int[count];
        docIds[0] = uri.hashCode();

        if (relatedDocs.length > 0) {
          for (int i = 0; i < relatedDocs.length; i++) {
            docIds[i+1] = relatedDocs[i].hashCode();
          }  
        }
          
        return docIds;
      }
    }

    @Override
    public BytesRef payload() {
      return currentPayload;
    }

    @Override
    public boolean hasPayloads() {
      return hasPayloads;
    }

    public int[] docIdsArray() {
      return currentDocIdsArray;
    }
    
    /** 
     * Returns the value of the <code>weightField</code> for the current document.
     * Retrieves the value for the <code>weightField</code> if its stored (using <code>doc</code>)
     * or if its indexed as {@link NumericDocValues} (using <code>docId</code>) for the document.
     * If no value is found, then the weight is 0.
     */
    protected long getWeight(Document doc, int docId) {
      IndexableField weight = doc.getField(weightField);
      if (weight != null) { // found weight as stored
        return (weight.numericValue() != null) ? weight.numericValue().longValue() : 0;
      } else if (weightValues != null) {  // found weight as NumericDocValue
        return weightValues.get(docId);
      } else { // fall back
        return 0;
      }
    }
    
    private Set<String> getRelevantFields(String... fields) {
      Set<String> relevantFields = new HashSet<>();
      for (String relevantField : fields) {
        if (relevantField != null) {
          relevantFields.add(relevantField);
        }
      }
      return relevantFields;
    }

    @Override
    public Set<BytesRef> contexts() {
      if (hasContexts) {
        return currentContexts;
      }
      return null;
    }

    @Override
    public boolean hasContexts() {
      return hasContexts;
    }
  }
}
