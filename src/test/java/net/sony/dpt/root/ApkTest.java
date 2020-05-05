package net.sony.dpt.root;

import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringBufferInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class ApkTest {

    private static final String APK_LOCATION = "apk/app-debug.apk";
    private static final String MANIFEST_NAME = "AndroidManifest.xml";


    @Test
    public void canOpenApk() throws IOException {

        try (ApkFile apkFile = new ApkFile(Objects.requireNonNull(getClass().getClassLoader().getResource(APK_LOCATION)).getFile())) {
            ApkMeta apkMeta = apkFile.getApkMeta();
            assertThat(apkMeta.getLabel(), is("DPT_template"));
        }

    }

    @Test
    public void canFindAllInfoFromManifest() throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
        // We need an activity and an action
        // Activity: /manifest/android:package + / + /manifest/application/activity/android:name
        // Action: we take the first intent filter

        ApkFile apkFile = new ApkFile(Objects.requireNonNull(getClass().getClassLoader().getResource(APK_LOCATION)).getFile());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new StringBufferInputStream(apkFile.getManifestXml()));

        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();

        String packageDefinition = apkFile.getApkMeta().getPackageName();

        XPathExpression expr = xpath.compile("/manifest/application/activity/@name");
        String activityName = expr.evaluate(doc);

        expr = xpath.compile("/manifest/application/activity/intent-filter/action/@name");
        String action = expr.evaluate(doc);

        expr = xpath.compile("/manifest/application/@icon");
        String iconPath = expr.evaluate(doc);

        byte[] icon = apkFile.getFileData(iconPath);
        assertThat(icon.length, is(10652));
    }

}
