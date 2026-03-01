package simpleapp.generated;

import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;
import javax.xml.transform.TransformerException;
import simpleapp.ExtensionComponents;
import simpleapp.ExtensionElementHandler;

public final class AppExtensionBridge {
  private AppExtensionBridge() {}

  private static ExtensionElementHandler handler(String name) throws TransformerException {
    ExtensionElementHandler h = ExtensionComponents.get(name);
    if (h == null) {
      throw new TransformerException("No hay handler para " + name);
    }
    return h;
  }

  public static void miExtension(XSLProcessorContext ctx, ElemExtensionCall elem) throws TransformerException {
    handler("miExtension").invoke(ctx, elem);
  }

}
