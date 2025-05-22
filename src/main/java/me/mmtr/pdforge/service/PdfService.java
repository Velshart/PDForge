package me.mmtr.pdforge.service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.types.ObjectId;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.layout.SharedContext;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class PdfService {

    private final MongoTemplate mongoTemplate;

    private final ITextRenderer renderer;

    public PdfService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.renderer = new ITextRenderer();
        SharedContext sharedContext = renderer.getSharedContext();

        sharedContext.setPrint(true);
        sharedContext.setInteractive(false);
    }

    public void saveAsPdf(String userId, String filename, String html, String delta) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            Document document = Jsoup.parse(html, "UTF-8");
            document.outputSettings().syntax(Document.OutputSettings.Syntax.xml);

            renderer.setDocumentFromString(document.html());
            renderer.layout();
            renderer.createPDF(byteArrayOutputStream);

            byteArrayOutputStream.close();

            try (InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {

                GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());

                org.bson.Document metadata = new org.bson.Document()
                        .append("type", "PDF file")
                        .append("delta", delta)
                        .append("userId", userId);

                final int MEGABYTE_IN_BYTES = 1048576;
                GridFSUploadOptions options = new GridFSUploadOptions()
                        .chunkSizeBytes(MEGABYTE_IN_BYTES)
                        .metadata(metadata);

                final String PDF_EXTENSION = ".pdf";
                gridFSBucket.uploadFromStream(filename + PDF_EXTENSION, inputStream, options);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void getAsPdf(String filename, ObjectId id) {

        try (FileOutputStream outputStream = new FileOutputStream(filename)) {
            GridFSBucket bucket = GridFSBuckets.create(mongoTemplate.getDb());

            bucket.downloadToStream(id, outputStream);

            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getAsByteArrayStream(ObjectId id) throws IOException {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        GridFSBucket bucket = GridFSBuckets.create(mongoTemplate.getDb());

        GridFSFile file = bucket.find(new org.bson.Document("_id", id)).first();

        if (file == null) {
            throw new IOException("File not found");
        }

        bucket.downloadToStream(file.getObjectId(), outputStream);
        return outputStream.toByteArray();
    }

    public List<GridFSFile> getUserGridFSFiles(String userId) {
        GridFSBucket bucket = GridFSBuckets.create(mongoTemplate.getDb());

        return bucket.find(new org.bson.Document("metadata.userId", userId)).into(new ArrayList<>());
    }

    public GridFSFile getAsGridFSFile(String userId, ObjectId objectId) {
        return getUserGridFSFiles(userId)
                .stream()
                .filter(gridFSFile ->
                        gridFSFile.getObjectId().equals(objectId))
                .findFirst()
                .orElse(null);
    }

    public void deleteGridFSFile(ObjectId id) {
        GridFSBucket bucket = GridFSBuckets.create(mongoTemplate.getDb());
        bucket.find(new org.bson.Document("_id", id))
                .forEach(file -> bucket.delete(file.getObjectId()));
    }
}
