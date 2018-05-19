/*
 *  Copyright 2012 BigData.mx
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package mx.bigdata.sat.cfdi;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import mx.bigdata.sat.cfdi.schema.ObjectFactory;
import mx.bigdata.sat.cfdi.schema.TimbreFiscalDigital;
import mx.bigdata.sat.common.ComprobanteBase;
import mx.bigdata.sat.common.NamespacePrefixMapperImpl;
import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;

public final class TFDv1c32 {

    private static final String XSLT = "/xslt/cadenaoriginal_TFD_1_0.xslt";

    private static final String XSD = "/xsd/v32/TimbreFiscalDigital.xsd";

    private static final JAXBContext CONTEXT = createContext();

    private static JAXBContext createContext() {
        if(CONTEXT==null) {
            try {
                return JAXBContext.newInstance("mx.bigdata.sat.cfdi.schema");
            } catch (JAXBException e) {
                throw new Error(e);
            }
        }
        return CONTEXT;
    }

    private final ComprobanteBase document;

    private final TimbreFiscalDigital tfd;

    private final X509Certificate cert;

    private TransformerFactory tf;

    public TFDv1c32(CFDI cfd, X509Certificate cert) throws Exception {
        this(cfd, cert, UUID.randomUUID(), new Date());
    }

    TFDv1c32(CFDI cfd, X509Certificate cert, UUID uuid, Date date)
            throws Exception {
        this.cert = cert;
        this.document = cfd.getComprobante();
        this.tfd = getTimbreFiscalDigital(document, uuid, date);
    }

    public void setTransformerFactory(TransformerFactory tf) {
        this.tf = tf;
    }

    public int timbrar(PrivateKey key) throws Exception {
        if (tfd.getSelloSAT() != null) {
            return 304;
        }
        String signature = getSignature(key);
        tfd.setSelloSAT(signature);
        stamp();
        return 300;
    }

    public void validar() throws Exception {
        validar(null);
    }

    public void validar(ErrorHandler handler) throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(getClass().getResource(XSD));
        Validator validator = schema.newValidator();
        if (handler != null) {
            validator.setErrorHandler(handler);
        }
        validator.validate(new JAXBSource(CONTEXT, tfd));
    }

    public int verificar() throws Exception {
        if (tfd == null) {
            return 601; //No contiene timbrado
        }
        Base64 b64 = new Base64();
        String sigStr = tfd.getSelloSAT();
        byte[] signature = b64.decode(sigStr);
        byte[] bytes = getOriginalBytes();
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initVerify(cert);
        sig.update(bytes);
        boolean verified = sig.verify(signature);
        return verified ? 600 : 602; //Sello del timbrado no valido
    }

    public String getCadenaOriginal() throws Exception {
        byte[] bytes = getOriginalBytes();
        return new String(bytes);
    }

    public void guardar(OutputStream out) throws Exception {
        Marshaller m = CONTEXT.createMarshaller();
        m.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapperImpl(CFDv32.PREFIXES));
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.sat.gob.mx/cfd/3 http://www.sat.gob.mx/sitio_internet/cfd/3/cfdv32.xsd");
        m.marshal(document.getComprobante(), out);
    }

    TimbreFiscalDigital getTimbre() {
        return tfd;
    }

    byte[] getOriginalBytes() throws Exception {
        JAXBSource in = new JAXBSource(CONTEXT, tfd);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Result out = new StreamResult(baos);
        TransformerFactory factory = tf;
        if (factory == null) {
            factory = TransformerFactory.newInstance();
        }
        Transformer transformer = factory.newTransformer(new StreamSource(getClass().getResourceAsStream(XSLT)));
        transformer.transform(in, out);
        return baos.toByteArray();
    }

    String getSignature(PrivateKey key) throws Exception {
        byte[] bytes = getOriginalBytes();
        Signature sig = Signature.getInstance("SHA1withRSA");
        sig.initSign(key);
        sig.update(bytes);
        byte[] signed = sig.sign();
        Base64 b64 = new Base64(-1);
        return b64.encodeToString(signed);
    }

    private void stamp() throws Exception {
        Element element = marshalTFD();
        document.setComplemento(element);
    }

    private Element marshalTFD() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        Marshaller m = CONTEXT.createMarshaller();
        m.setProperty("com.sun.xml.bind.namespacePrefixMapper", new NamespacePrefixMapperImpl(CFDv3.PREFIXES));
        m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
        m.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, "http://www.sat.gob.mx/TimbreFiscalDigital TimbreFiscalDigital.xsd");
        m.marshal(tfd, doc);
        return doc.getDocumentElement();
    }

    private TimbreFiscalDigital createStamp(UUID uuid, Date date) {
        ObjectFactory of = new ObjectFactory();
        TimbreFiscalDigital tfds = of.createTimbreFiscalDigital();
        tfds.setVersion("1.0");
        tfds.setFechaTimbrado(date);
        tfds.setSelloCFD(document.getSello());
        tfds.setUUID(uuid.toString());
        BigInteger bi = cert.getSerialNumber();
        tfds.setNoCertificadoSAT(new String(bi.toByteArray()));
        return tfds;
    }

    private TimbreFiscalDigital getTimbreFiscalDigital(ComprobanteBase document, UUID uuid, Date date) throws Exception {
        if (document.hasComplemento()) {
            List<Object> list = document.getComplementoGetAny();
            for (Object o : list) {
                if (o instanceof TimbreFiscalDigital) {
                    return (TimbreFiscalDigital) o;
                }
            }
        }
        return createStamp(uuid, date);
    }

}
