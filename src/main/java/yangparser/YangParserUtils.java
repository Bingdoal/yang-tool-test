package yangparser;

import helper.YangUtils;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class YangParserUtils {
    public static void yangToJsonFile() throws ReactorException, YangSyntaxErrorException, IOException, URISyntaxException, ClassNotFoundException {
        clear();
        initDirectory();

        EffectiveSchemaContext schemaContext = YangUtils.getSchemaContext();
        YangToJson yangToJson = new YangToJson();
        yangToJson.convertToDto(schemaContext);
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
