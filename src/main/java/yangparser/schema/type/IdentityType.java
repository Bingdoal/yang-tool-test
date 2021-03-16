package yangparser.schema.type;

import lombok.Data;

import java.util.Optional;

@Data
public class IdentityType {
    private Optional<String> description;
    private String identity;
}
