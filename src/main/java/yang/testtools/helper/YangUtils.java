package yang.testtools.helper;

import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.StatementParserMode;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.reactor.RFC7950Reactors;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class YangUtils {
    public static EffectiveSchemaContext schemaContext;

    public static EffectiveSchemaContext getSchemaContext(String... paths) throws ReactorException, YangSyntaxErrorException, IOException, URISyntaxException, ClassNotFoundException {

        List<File> files = new ArrayList<>();
        for (String path : paths) {
            File[] folderFiles = getYangFiles(path);
            if (folderFiles == null) {
                System.out.println("If use window, the path is must under System Disk.");
            }
            files.addAll(Arrays.asList(folderFiles));
        }

        Collection<YangStatementStreamSource> sources = new ArrayList<>(files.size());
        for (File file : files) {
            sources.add(YangStatementStreamSource.create(YangTextSchemaSource.forFile(file)));
        }
        final CrossSourceStatementReactor.BuildAction reactor =
                RFC7950Reactors.defaultReactor().newBuild(StatementParserMode.DEFAULT_MODE)
                        .addSources(sources);
        EffectiveSchemaContext schemaContext = reactor.buildEffective();


        return schemaContext;
    }

    private static File[] getYangFiles(String path) throws IOException {
        FileFilter YANG_FILE_FILTER =
                file -> file.getName().endsWith(YangConstants.RFC6020_YANG_FILE_EXTENSION) && file.isFile();
        File sourcesDir = new File(path);
        return sourcesDir.listFiles(YANG_FILE_FILTER);
    }

    public static String getNamespace(String moduleName) {
        return schemaContext.findModules(moduleName).iterator().next().getNamespace().toString();
    }

    public static Module findModule(String moduleName) {
        return schemaContext.findModules(moduleName).iterator().next();
    }
}
