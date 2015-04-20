import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.transform.stream.StreamResult
import java.io.File
import java.io.FileFilter

def transform(dataXML: String, inputXSL: String, outputHTML: String, index: Int, totalFiles: Int) = {
  try {
    val factory = TransformerFactory.newInstance();
    val xslStream = new StreamSource(inputXSL);
    val transformer = factory.newTransformer(xslStream);

    val inFile = new File(dataXML);
    val in = new StreamSource(inFile);
    val out = new StreamResult(outputHTML);
    transformer.transform(in, out);
    System.out.println("[" + index + "/" + totalFiles + " The generated XML file is:" + outputHTML);
  } catch {
    case e: Exception => println(e)
  }
}

// ---- MAIN ----
// EXAMPLE createSolrDocs.sh data/downloaded/ data/solr_docs ../../test/chp03/dbpedia_start/dbpediaToPost.xslt 

val rdfDir = new File(args { 0 }).getCanonicalFile()
val outDir = new File(args { 1 }).getCanonicalFile()
if (!outDir.exists()) outDir.mkdirs()
val xslt = args { 2 }

// if the directory containing RDF is wrong or does not exists, throw an exception
if (!rdfDir.exists()) throw new RuntimeException(rdfDir + " does not exists!")

val files = rdfDir.listFiles(new FileFilter() {
  override def accept(pathname: File): Boolean = {
    pathname.getName().endsWith(".rdf.xml")
  }
}).toList

val totalFiles = files.length;

files.zipWithIndex.foreach{case (f, index) => {
  transform(f.getAbsolutePath(), xslt, new File(outDir, f.getName().replaceAll("\\.rdf\\.", "\\.post\\.")).getAbsolutePath(), index, totalFiles);
}};

