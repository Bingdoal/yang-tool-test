package schema;

import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
public class ModuleDto {
    private String name;
    private String namespace;
    private String revision;
    private Optional<DataTreeDto> dataTree;
    private Optional<Map<String, RpcDto>> rpcs;
    private Optional<Map<String, ContainerDto>> notifications;
}
