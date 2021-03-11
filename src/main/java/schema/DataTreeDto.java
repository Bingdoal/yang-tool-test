package schema;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
public class DataTreeDto {
    private Optional<Map<String, LeafDto>> leaves;
    private Optional<Map<String, ContainerDto>> containers;

    public boolean isEmpty() {
        return (leaves == null || leaves.isEmpty())
                && (containers == null || containers.isEmpty());
    }
}
