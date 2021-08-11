package yang.testtools.yangparser.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
@JsonIgnoreProperties({"empty"})
public class ModuleDto {
    private String name;
    private String namespace;
    private String revision;
    private Optional<String> description;
    private DataTreeDto dataTree;
    private Map<String, RpcDto> rpc;
    @JsonIgnoreProperties({"config", "xpath"})
    private Map<String, ContainerDto> notification;

    public boolean isEmpty() {
        return dataTree == null && rpc == null && notification == null;
    }
}
