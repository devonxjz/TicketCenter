package vn.ticketscenter.event.service;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.dto.EventResponseValues;
import static org.junit.jupiter.api.Assertions.*;
class EventResponseValuesTest {
    @Test void sqlEscapedSlashesAreDecodedBeforeComparingCoverMetadata(){assertEquals("https://cdn.example/image.png",EventResponseValues.string("{\"data\":{\"coverImageUrl\":\"https:\\/\\/cdn.example\\/image.png\"}}","coverImageUrl").orElseThrow());}
    @Test void aFieldMentionedInsideDescriptionDoesNotShadowRealMetadata(){assertEquals("https://real.example/a.png",EventResponseValues.string("{\"data\":{\"description\":\"\\\"coverImageUrl\\\":\\\"fake\\\"\",\"coverImageUrl\":\"https://real.example/a.png\"}}","coverImageUrl").orElseThrow());}
}
