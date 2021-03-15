package yangparser.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
@JsonIgnoreProperties(value = {"empty"})
public class ModuleDto {
    private String name;
    private String namespace;
    private String revision;
    private Optional<String> description;
    private Optional<DataTreeDto> dataTree;
    private Optional<Map<String, RpcDto>> rpc;
    private Optional<Map<String, ContainerDto>> notification;

    public boolean isEmpty() {
        return dataTree == null && rpc == null && notification == null;
    }
}
