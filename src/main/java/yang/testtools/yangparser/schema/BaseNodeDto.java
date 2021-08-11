package yang.testtools.yangparser.schema;

import lombok.Data;
import org.opendaylight.yangtools.yang.model.api.Status;

import java.util.List;
import java.util.Optional;

@Data
public class BaseNodeDto {
    protected Optional<String> when;
    protected Optional<List<String>> must;
    protected Optional<List<String>> ifFeature;
    protected boolean isArray = false;
    protected boolean config = true;
    protected Status status;
    protected String xpath;
    //    protected String xpath;
    protected Optional<String> description;
}
