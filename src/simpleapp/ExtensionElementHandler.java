package simpleapp;

import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;

import javax.xml.transform.TransformerException;

/**
 * Contrato simple para componentes que implementan elementos de extension XSLT.
 */
public interface ExtensionElementHandler {
    void invoke(XSLProcessorContext context, ElemExtensionCall element) throws TransformerException;
}
