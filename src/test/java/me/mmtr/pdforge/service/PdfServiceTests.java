package me.mmtr.pdforge.service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@SpringBootTest
public class PdfServiceTests {

    private final String firstFilename = UUID.randomUUID().toString();
    private final String secondFilename = UUID.randomUUID().toString();
    private final String FIRST_USER_ID = "1";
    private final String EXTENSION = ".pdf";
    private ObjectId firstObjectId;
    private ObjectId secondObjectId;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private MongoTemplate mongoTemplate;
    private GridFSBucket gridFSBucket;

    @BeforeEach
    public void setUp() {
        final String SECOND_USER_ID = "2";

        gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
        firstObjectId = pdfService.saveAsPdf(
                FIRST_USER_ID,
                firstFilename,
                "<p>Test 1</p>",
                "some delta 1"
        );

        secondObjectId = pdfService.saveAsPdf(
                SECOND_USER_ID,
                secondFilename,
                "<p>Test 2</p>",
                "some delta 2"
        );
    }

    @AfterEach
    public void tearDown() {
        gridFSBucket.find(new Document("_id", firstObjectId)).forEach(gridFSObject ->
                gridFSBucket.delete(gridFSObject.getObjectId()));

        gridFSBucket.find(new Document("_id", secondObjectId)).forEach(gridFSObject ->
                gridFSBucket.delete(gridFSObject.getObjectId()));
    }

    @Test
    public void shouldCorrectlySaveAsPdf() {
        GridFSFile gridFSFile = gridFSBucket.find(new Document("filename", firstFilename + EXTENSION))
                .first();
        Assertions.assertNotNull(gridFSFile);
        Assertions.assertEquals(firstFilename + EXTENSION, gridFSFile.getFilename());
    }

    @Test
    public void shouldCorrectlyFindAndReturnFileAsByteArrayStream()
            throws IOException {
        String contentToSave = "Some string for testing";
        InputStream inputStream = new ByteArrayInputStream(contentToSave.getBytes());

        GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());

        ObjectId objectId = gridFSBucket.uploadFromStream(firstFilename + EXTENSION, inputStream);

        byte[] bytes = pdfService.getAsByteArrayStream(objectId);

        Assertions.assertEquals(contentToSave, new String(bytes));

        gridFSBucket.find(new Document("_id", objectId))
                .forEach(file -> gridFSBucket.delete(file.getObjectId()));
    }

    @Test
    public void shouldThrowAnExceptionWhenTryingToGetNonExistingFileAsByteArrayStream() {
        Assertions.assertThrows(IOException.class, () ->
                pdfService.getAsByteArrayStream(new ObjectId())
        );
    }

    @Test
    public void shouldCorrectlyReturnUserFiles() {
        List<GridFSFile> userFiles = pdfService.getUserGridFSFiles(FIRST_USER_ID);
        Assertions.assertEquals(1, userFiles.size());
        GridFSFile file = userFiles.getFirst();

        Assertions.assertNotNull(file.getMetadata());
        Assertions.assertEquals(FIRST_USER_ID, file.getMetadata().get("userId"));
    }

    @Test
    public void shouldCorrectlyReturnGridFSFile() {

        GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());

        GridFSFile foundFile = gridFSBucket.find(
                new Document("filename", firstFilename + EXTENSION)
        ).first();

        Assertions.assertNotNull(foundFile);

        ObjectId foundFileObjectId = foundFile.getObjectId();
        GridFSFile file = pdfService.getAsGridFSFile(FIRST_USER_ID, foundFileObjectId);

        Assertions.assertEquals(file, foundFile);
    }

    @Test
    public void shouldCorrectlyDeleteGridFSFile() {
        GridFSFile foundFile = gridFSBucket.find(
                new Document("filename", firstFilename + EXTENSION)
        ).first();

        Assertions.assertNotNull(foundFile);

        ObjectId foundFileObjectId = foundFile.getObjectId();
        gridFSBucket.delete(foundFile.getObjectId());
        Assertions.assertNull(
                gridFSBucket.find(new Document("_id", foundFileObjectId)).first()
        );
    }
}
