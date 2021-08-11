package yang.testtools.odlservice;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import yang.testtools.helper.YangUtils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

public class FilterParser {

    public static String xmlParser(String namespace, String path) {
        String xmlStr = "";
        String[] paths = path.split("[\\/\\\\]+");
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            // root elements
            Document doc = docBuilder.newDocument();
            Element rootElement = doc.createElementNS(namespace, paths[0]);

            Element childLink = rootElement;

            for (int i = 1; i < paths.length; i++) {
                String[] node = paths[i].split(":");
                String nodeName = paths[i];
                Element childElement = null;
                if (node.length > 1) {
                    String moduleName = node[0];
                    nodeName = node[1];
                    String otherNS = YangUtils.getNamespace(moduleName);
                    nodeName = nodeName.replaceAll("[\\{\\}]", "");
                    childElement = doc.createElementNS(otherNS, nodeName);
                } else {
                    nodeName = nodeName.replaceAll("[\\{\\}]", "");
                    childElement = doc.createElement(nodeName);
                }
                childLink.appendChild(childElement);

                childLink = childElement;
            }
            doc.appendChild(rootElement);

            xmlStr = xmlToString(doc);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        return xmlStr;
    }


    private static String xmlToString(Document doc) {
        try {
            StringWriter sw = new StringWriter();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();

            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            transformer.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error converting to String", ex);
        }
    }
}
