package schema;

import lombok.Data;

import java.util.*;

@Data
public class ContainerDto {
    private boolean isList = false;
    private boolean config = true;
    private String status;
    private Optional<String> key; // list only
    private Optional<Map<String, LeafDto>> leaves;
    private Optional<Map<String, ContainerDto>> containers;
}
