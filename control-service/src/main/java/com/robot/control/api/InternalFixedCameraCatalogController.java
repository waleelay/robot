package com.robot.control.api;

import com.robot.control.fixedcamera.FixedCameraCatalogLeaseRequest;
import com.robot.control.fixedcamera.FixedCameraCatalogLeaseService;
import com.robot.control.fixedcamera.FixedCameraCatalogSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BFF 与 Control 之间的固定摄像头目录租约入口，不向浏览器暴露。 */
@RestController
@RequestMapping("/internal/control/fixed-camera-catalog-leases")
public class InternalFixedCameraCatalogController {

    private final FixedCameraCatalogLeaseService leaseService;

    @Value("${control.fixed-camera-catalog.trusted-caller:bigscreen-bff}")
    private String trustedCaller = "bigscreen-bff";

    public InternalFixedCameraCatalogController(FixedCameraCatalogLeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PutMapping
    public FixedCameraCatalogSnapshot upsert(
            @RequestBody FixedCameraCatalogLeaseRequest request,
            HttpServletRequest servletRequest) {
        String caller = servletRequest.getHeader("X-Internal-Caller");
        if (caller == null || !caller.equals(trustedCaller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "固定摄像头目录租约调用方不受信任");
        }
        return leaseService.upsert(request);
    }

    /** BFF 最后一个同身份会话关闭时主动撤销目录租约。 */
    @DeleteMapping("/{leaseId}")
    public void release(@PathVariable String leaseId, HttpServletRequest servletRequest) {
        String caller = servletRequest.getHeader("X-Internal-Caller");
        if (caller == null || !caller.equals(trustedCaller)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "固定摄像头目录租约调用方不受信任");
        }
        leaseService.release(leaseId);
    }
}
