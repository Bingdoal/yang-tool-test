package schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.*;

@Data
@JsonIgnoreProperties(value = { "empty" })
public class ContainerDto {
    private boolean isList = false;
    private boolean config = true;
    private Optional<String> description;
    private Optional<String> key; // list only
    private Optional<Map<String, LeafDto>> leaves;
    private Optional<Map<String, ContainerDto>> containers;

    public boolean isEmpty() {
        return (leaves == null || leaves.isEmpty())
                && (containers == null || containers.isEmpty());
    }
}
