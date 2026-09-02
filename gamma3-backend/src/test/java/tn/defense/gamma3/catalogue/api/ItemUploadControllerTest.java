package tn.defense.gamma3.catalogue.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import tn.defense.gamma3.catalogue.domain.Item;
import tn.defense.gamma3.catalogue.repository.ItemRepository;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ItemUploadControllerTest {

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ItemUploadController itemUploadController;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Change the upload directory to a temp dir so we don't write to the real file system during testing
        ReflectionTestUtils.setField(itemUploadController, "UPLOAD_DIR", tempDir.toAbsolutePath().toString() + "/");
    }

    @Test
    void testUploadWithValidPath() {
        UUID itemId = UUID.randomUUID();
        Item mockItem = new Item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));
        when(itemRepository.save(any(Item.class))).thenReturn(mockItem);

        MultipartFile file = new MockMultipartFile("file", "valid.txt", "text/plain", "content".getBytes());

        ResponseEntity<Item> response = itemUploadController.uploadDocument(itemId, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testUploadWithPathTraversalInFilename() {
        UUID itemId = UUID.randomUUID();
        Item mockItem = new Item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        MultipartFile file = new MockMultipartFile("file", "../../../etc/passwd", "text/plain", "content".getBytes());

        ResponseEntity<Item> response = itemUploadController.uploadDocument(itemId, file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testUploadWithNullFilename() {
        UUID itemId = UUID.randomUUID();
        Item mockItem = new Item();
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(mockItem));

        MultipartFile file = new MockMultipartFile("file", "", "text/plain", "content".getBytes()) {
            @Override
            public String getOriginalFilename() {
                return null;
            }
        };

        ResponseEntity<Item> response = itemUploadController.uploadDocument(itemId, file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
