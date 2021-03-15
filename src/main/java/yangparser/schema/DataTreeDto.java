package yangparser.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;
import java.util.Optional;

@Data
@JsonIgnoreProperties(value = {"empty"})
public class DataTreeDto {
    private Optional<String> description;
    private Optional<Map<String, LeafDto>> leaf;
    private Optional<Map<String, ContainerDto>> container;
    private Optional<Map<String, LeafDto>> leafList;
    private Optional<Map<String, ContainerDto>> list;

    public boolean isEmpty() {
        return (leaf == null || leaf.isEmpty())
                && (leafList == null || leafList.isEmpty())
                && (container == null || container.isEmpty())
                && (list == null || list.isEmpty());
    }
}
