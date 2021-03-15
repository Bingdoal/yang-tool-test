import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.StatementParserMode;
import org.opendaylight.yangtools.yang.parser.rfc7950.reactor.RFC7950Reactors;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import schema.ModuleDto;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

public class Main {
    public static EffectiveSchemaContext schemaContext;

    public static void main(String[] args) throws IOException, YangSyntaxErrorException, ReactorException, URISyntaxException {
        clear();
        initDirectory();

        YangToJson yangToJson = new YangToJson();
        final Collection<YangStatementStreamSource> sources = yangToJson.getSource("oran/");
        final CrossSourceStatementReactor.BuildAction reactor =
                RFC7950Reactors.defaultReactor().newBuild(StatementParserMode.DEFAULT_MODE)
                        .addSources(sources);

        schemaContext = reactor.buildEffective();

        yangToJson.convertToDto(schemaContext);


//        Module module = schemaContext.findModules("ietf-netconf-acm").iterator().next();
//        ModuleDto moduleDto = yangToJson.convertToDto(schemaContext,module);
//        ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(new Jdk8Module());
//        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//        String json = mapper.writeValueAsString(moduleDto);
//        System.out.println(json);
    }

    public static void clear() {
        deleteDirectory(Paths.get("./parser_result").toFile());
    }

    private static void initDirectory(){
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

    public static void printIndent(int number) {
        for (int i = 0; i < number; i++) {
            System.out.print("\t");
        }
    }

}
