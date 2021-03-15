package yangparser;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.StatementParserMode;
import org.opendaylight.yangtools.yang.parser.rfc7950.reactor.RFC7950Reactors;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import yangparser.schema.ModuleDto;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

public class YangParserUtils {
    public static void yangToJsonFile(String path) throws ReactorException, YangSyntaxErrorException, IOException, URISyntaxException {
        clear();
        initDirectory();

        YangToJson yangToJson = new YangToJson();
        final Collection<YangStatementStreamSource> sources = yangToJson.getSource(path);
        final CrossSourceStatementReactor.BuildAction reactor =
                RFC7950Reactors.defaultReactor().newBuild(StatementParserMode.DEFAULT_MODE)
                        .addSources(sources);

        EffectiveSchemaContext schemaContext = reactor.buildEffective();

        yangToJson.convertToDto(schemaContext);
    }

    public static String yangToJsonDebug(String path,String moduleName) throws YangSyntaxErrorException, IOException, URISyntaxException, ReactorException {
        YangToJson yangToJson = new YangToJson();
        final Collection<YangStatementStreamSource> sources = yangToJson.getSource(path);
        final CrossSourceStatementReactor.BuildAction reactor =
                RFC7950Reactors.defaultReactor().newBuild(StatementParserMode.DEFAULT_MODE)
                        .addSources(sources);

        EffectiveSchemaContext schemaContext = reactor.buildEffective();

        Module module = schemaContext.findModules(moduleName).iterator().next();
        ModuleDto moduleDto = yangToJson.convertToDto(schemaContext, module);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Jdk8Module());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper.writeValueAsString(moduleDto);
    }

    private static void clear() {
        deleteDirectory(Paths.get("./parser_result").toFile());
    }

    private static void initDirectory() {
        try {
            Files.createDirectory(Paths.get("./parser_result"));
            Files.createDirectory(Paths.get("./parser_result/empty"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}
