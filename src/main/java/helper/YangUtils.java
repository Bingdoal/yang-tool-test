package helper;

import org.opendaylight.yangtools.yang.common.YangConstants;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.StatementParserMode;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.parser.rfc7950.reactor.RFC7950Reactors;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.EffectiveSchemaContext;
import yangparser.YangToJson;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

public class YangUtils {
    public static EffectiveSchemaContext schemaContext;

    public static EffectiveSchemaContext getSchemaContext(String path) throws ReactorException, YangSyntaxErrorException, IOException, URISyntaxException {
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

        return reactor.buildEffective();
    }

    public static String getNamespace(String moduleName) {
        return schemaContext.findModules(moduleName).iterator().next().getNamespace().toString();
    }
}
