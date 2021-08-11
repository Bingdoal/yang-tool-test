package yang.testtools.yangparser.schema.type;

import lombok.Data;

import java.util.Optional;

@Data
public class BitsType {
    private String name;
    private int position;
    private Optional<String> description;

    @Override
    public String toString() {
        return position + ": " + name;
    }
}
