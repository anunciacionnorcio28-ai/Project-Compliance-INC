package app.privmicro.service;

import app.privmicro.model.Media;
import app.privmicro.model.MicroPost;
import app.privmicro.repo.MediaRepository;
import app.privmicro.repo.PostRepository;
import app.privmicro.repo.UserRepository;
import app.privmicro.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.UUID;

/**
 * Metadata stripping: every upload is decoded to raw pixels and re-encoded from scratch, so EXIF, GPS, XMP,
 * ICC and any other embedded metadata (or trailing payload) never reach storage.
 */
@Service
public class MediaService {
    private static final long MAX_PIXELS = 40_000_000L;

    private final MediaRepository media;
    private final PostRepository posts;
    private final UserRepository users;
    private final PostService postService;

    public MediaService(MediaRepository media, PostRepository posts, UserRepository users, PostService postService) {
        this.media = media;
        this.posts = posts;
        this.users = users;
        this.postService = postService;
    }

    @Transactional
    public String store(AuthUser me, MultipartFile file) {
        byte[] clean = strip(file);
        Media m = new Media();
        m.setId(UUID.randomUUID().toString());
        m.setOwner(users.getReferenceById(me.id()));
        m.setContentType(clean[0] == (byte) 0x89 ? "image/png" : "image/jpeg");
        m.setData(clean);
        media.save(m);
        return m.getId();
    }

    @Transactional(readOnly = true)
    public Media load(AuthUser me, String id) {
        Media m = media.findWithOwner(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Image not found"));
        if (m.getOwner().getId() == me.id()) return m;
        MicroPost p = posts.findByMediaIdWithUser(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Image not found"));
        if (!postService.canView(p, me.id())) throw new ApiException(HttpStatus.NOT_FOUND, "Image not found");
        return m;
    }

    private byte[] strip(MultipartFile file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw unsupported();
            ImageReader reader = readers.next();
            String format = reader.getFormatName().toLowerCase();
            boolean jpeg = format.equals("jpeg") || format.equals("jpg");
            if (!jpeg && !format.equals("png")) throw unsupported();
            reader.setInput(in, true, true);
            if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Image dimensions are too large");
            BufferedImage src = reader.read(0);
            reader.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (jpeg) {
                BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.drawImage(src, 0, 0, Color.WHITE, null);
                g.dispose();
                ImageIO.write(rgb, "jpg", out);
            } else {
                ImageIO.write(src, "png", out);
            }
            return out.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw unsupported();
        }
    }

    private static ApiException unsupported() {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG and PNG images are supported");
    }
}
