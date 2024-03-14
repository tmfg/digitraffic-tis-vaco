package fi.digitraffic.tis.utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Archiver {

    private Archiver() {}

    /**
     * Recursively creates a ZIP archive from given directory to given file path.
     * @param sourceFolder Directory to archive.
     * @param targetFile Resulting file.
     * @throws IOException If operation could not be completed succesfully.
     */
    public static void createZip(Path sourceFolder, Path targetFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile.toFile());
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {
            addItemToZip(sourceFolder, sourceFolder, zipOut);
        }
    }

    private static void addItemToZip(Path root, Path itemToZip, ZipOutputStream zipOut) throws IOException {
        Path relativized = root.relativize(itemToZip);

        if (Files.isDirectory(itemToZip)) {
            String folderName = relativized.toString();
            // Avoiding root "downloads" folder being included in the zip's file structure
            boolean isRootFolder = folderName.isBlank();
            if (!isRootFolder) {
                zipOut.putNextEntry(new ZipEntry(folderName + "/"));
                zipOut.closeEntry();
            }
            File[] folderContents = itemToZip.toFile().listFiles();
            if (folderContents != null) {
                for (File file : folderContents) {
                    addItemToZip(root, file.toPath(), zipOut);
                }
            }
        } else {
            ZipEntry zipEntry = new ZipEntry(relativized.toString());
            zipOut.putNextEntry(zipEntry);
            Files.copy(itemToZip, zipOut);
            zipOut.closeEntry();
        }
    }
}
