package me.mmtr.pdforge.service;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSDownloadOptions;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
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

    private final int MEGABYTE_IN_BYTES = 1048576;
    private final String PDF_EXTENSION = ".pdf";

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

                GridFSUploadOptions options = new GridFSUploadOptions()
                        .chunkSizeBytes(MEGABYTE_IN_BYTES)
                        .metadata(metadata);

                gridFSBucket.uploadFromStream(filename + PDF_EXTENSION, inputStream, options);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public void getAsPdf(String filename) {
        String filePath = filename + PDF_EXTENSION;
        GridFSDownloadOptions options = new GridFSDownloadOptions().revision(0);

        try (FileOutputStream outputStream = new FileOutputStream(filePath)) {
            GridFSBucket bucket = GridFSBuckets.create(mongoTemplate.getDb());

            bucket.downloadToStream(filePath, outputStream, options);

            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public List<GridFSFile> getUserGridFSFiles(String userId) {
        GridFSBucket bucket = GridFSBuckets.create(mongoTemplate.getDb());

        return bucket.find(new org.bson.Document("metadata.userId", userId)).into(new ArrayList<>());
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
        GridFSBucket bucket = GridFSBuckets.create(mongoTemplate.getDb());

        bucket.find(new org.bson.Document("metadata.userId", userId)
                        .append("filename", filename + PDF_EXTENSION))
                .forEach(file -> bucket.delete(file.getObjectId()));
    }
}
