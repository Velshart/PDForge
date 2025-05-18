package me.mmtr.pdforge.service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSDownloadOptions;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.types.ObjectId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfService {

    private final int MEGABYTE_IN_BYTES = 1048576;
    @Value(value = "${spring.data.mongodb.uri}")
    private String mongoDatabaseUri;

    @Value(value = "${spring.data.mongodb.database}")
    private String mongoDatabaseName;

    private final MongoTemplate mongoTemplate;

    public PdfService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void saveAsPdf(String userId, String filename, String html, String delta) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            Document document = Jsoup.parse(html, "UTF-8");
            document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

            ITextRenderer renderer = new ITextRenderer();
            SharedContext sharedContext = renderer.getSharedContext();
            sharedContext.setPrint(true);
            sharedContext.setInteractive(false);

            renderer.setDocumentFromString(document.html());
            renderer.layout();
            renderer.createPDF(byteArrayOutputStream);

            byteArrayOutputStream.close();

            try (MongoClient mongoClient = MongoClients.create(mongoDatabaseUri);
                 InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {

                MongoDatabase database = mongoClient.getDatabase(mongoDatabaseName);
                GridFSBucket gridFSBucket = GridFSBuckets.create(database);

                org.bson.Document metadata = new org.bson.Document()
                        .append("type", "PDF file")
                        .append("delta", delta)
                        .append("userId", userId);

                GridFSUploadOptions options = new GridFSUploadOptions()
                        .chunkSizeBytes(MEGABYTE_IN_BYTES)
                        .metadata(metadata);

                ObjectId fileId = gridFSBucket.uploadFromStream(filename + ".pdf", inputStream, options);
                System.out.println("File id is: " + fileId);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public void getAsPdf(String filename) {
        String filePath = filename + ".pdf";
        GridFSDownloadOptions options = new GridFSDownloadOptions().revision(0);

        try (FileOutputStream outputStream = new FileOutputStream(filePath);
             MongoClient mongoClient = MongoClients.create(mongoDatabaseUri)
        ) {
            MongoDatabase database = mongoClient.getDatabase(mongoDatabaseName);
            GridFSBucket bucket = GridFSBuckets.create(database);

            bucket.downloadToStream(filePath, outputStream, options);

            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<GridFSFile> getUserGridFSFiles(String userId) {
        try (MongoClient mongoClient = MongoClients.create(mongoDatabaseUri)) {
            MongoDatabase database = mongoClient.getDatabase("pdforge");
            GridFSBucket bucket = GridFSBuckets.create(database);

            return bucket.find(new org.bson.Document("metadata.userId", userId)).into(new ArrayList<>());
        }
    }

    public GridFSFile getAsGridFSFile(String userId, String filename) {
        return getUserGridFSFiles(userId)
                .stream()
                .filter(file -> {
                    assert file.getMetadata() != null;
                    return file.getMetadata().get("userId").equals(userId);
                })
                .filter(file ->
                        file.getFilename().equals(filename)).findFirst().orElse(null);
    }

    public void deleteGridFSFile(String userId, String filename) {
        try (MongoClient mongoClient = MongoClients.create(mongoDatabaseUri)) {
            MongoDatabase database = mongoClient.getDatabase("pdforge");
            GridFSBucket bucket = GridFSBuckets.create(database);

            bucket.find(new org.bson.Document("metadata.userId", userId).append("filename", filename + ".pdf"))
                    .forEach(file -> bucket.delete(file.getObjectId()));
        }
    }
}
