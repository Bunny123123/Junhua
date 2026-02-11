package com.example.simpleapp.generated;

import org.apache.xalan.extensions.XSLProcessorContext;
import org.apache.xalan.templates.ElemExtensionCall;
import javax.xml.transform.TransformerException;
import com.example.simpleapp.ExtensionComponents;
import com.example.simpleapp.ExtensionElementHandler;

public final class AppExtensionBridge {
  private AppExtensionBridge() {}

  private static ExtensionElementHandler handler(String name) throws TransformerException {
    ExtensionElementHandler h = ExtensionComponents.get(name);
    if (h == null) {
      throw new TransformerException("No hay handler para " + name);
    }
    return h;
  }

  public static void saludo(XSLProcessorContext ctx, ElemExtensionCall elem) throws TransformerException {
    handler("saludo").invoke(ctx, elem);
  }

  public static void changeImageFormat(XSLProcessorContext ctx, ElemExtensionCall elem) throws TransformerException {
    handler("changeImageFormat").invoke(ctx, elem);
  }

}
