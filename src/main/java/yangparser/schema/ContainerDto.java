package yangparser.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.*;

@Data
@JsonIgnoreProperties(value = {"empty", "array"})
public class ContainerDto {
    private String path;
    private boolean isArray = false;
    private boolean config = true;
    private Optional<String> description;
    private Optional<String> key; // list only
    private Optional<Map<String, LeafDto>> leaf;
    private Optional<Map<String, ContainerDto>> container;
    private Optional<Map<String, LeafDto>> leafList;
    private Optional<Map<String, ContainerDto>> list;

    public boolean isEmpty() {
        return (description == null || description.isEmpty())
                && (leaf == null || leaf.isEmpty())
                && (leafList == null || leafList.isEmpty())
                && (container == null || container.isEmpty())
                && (list == null || list.isEmpty());
    }
}
