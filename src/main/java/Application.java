import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableSet;
import helper.YangUtils;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.FeatureDefinition;
import org.opendaylight.yangtools.yang.model.api.stmt.ModuleEffectiveStatement;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.StatementParserMode;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.reactor.RFC7950Reactors;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import yangparser.YangParserUtils;
import yangparser.YangToJson;
import org.opendaylight.yangtools.yang.model.api.Module;
import yangparser.schema.ModuleDto;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class Application {


    public static void main(String[] args) throws IOException, YangSyntaxErrorException, ReactorException, URISyntaxException {
        YangUtils.schemaContext = YangUtils.getSchemaContext("/oran");
        YangParserUtils.yangToJsonFile("/oran");

//        test();
    }

    private static void test() throws IOException, ReactorException, YangSyntaxErrorException, URISyntaxException {
        String path = "/oran";
        FileFilter YANG_FILE_FILTER =
                file -> file.getName().endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION) && file.isFile();
        URL url = YangUtils.class.getResource(path);
        if (url == null) {
            url = YangUtils.class.getClass().getClassLoader().getResource(path);
        }
        File sourcesDir = new File(url.toURI());
        File[] files = sourcesDir.listFiles(YANG_FILE_FILTER);
        Collection<YangStatementStreamSource> sources = new ArrayList<>(files.length);
        for (File file : files) {
            sources.add(YangStatementStreamSource.create(YangTextSchemaSource.forFile(file)));
        }
        final CrossSourceStatementReactor.BuildAction reactor =
                RFC7950Reactors.defaultReactor().newBuild(StatementParserMode.DEFAULT_MODE)
                        .addSources(sources);

        reactor.setSupportedFeatures(
                Set.of(
                        QName.create("urn:ietf:params:xml:ns:yang:ietf-hardware", "entity-mib"),
                        QName.create("urn:ietf:params:xml:ns:yang:ietf-hardware", "hardware-state"),
                        QName.create("urn:ietf:params:xml:ns:yang:ietf-hardware", "hardware-sensor")
                )
        );
        EffectiveSchemaContext schemaContext = reactor.buildEffective();

        Module module = schemaContext.findModules("ietf-hardware").iterator().next();
        for (FeatureDefinition feature : module.getFeatures()) {

            System.out.println(feature.getQName().getNamespace());
            System.out.println(feature.getQName().getLocalName());
        }

        YangToJson yangToJson = new YangToJson();
        ModuleDto moduleDto = yangToJson.convertToDto(
                schemaContext,
                module);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        String json = mapper.writeValueAsString(moduleDto);
        System.out.println(json);
    }
}
