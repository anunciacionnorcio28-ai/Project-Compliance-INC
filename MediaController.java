package app.privmicro.web;

import app.privmicro.dto.Dtos.MediaResponse;
import app.privmicro.model.Media;
import app.privmicro.security.AuthUser;
import app.privmicro.service.MediaService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaResponse upload(@AuthenticationPrincipal AuthUser me, @RequestPart("file") MultipartFile file) {
        return new MediaResponse(media.store(me, file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        Media m = media.load(me, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.getContentType()))
                .cacheControl(CacheControl.noStore()).body(m.getData());
    }
}
