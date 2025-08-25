package com.example.rednote.auth.model.admin.controller;



import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.rednote.auth.common.service.RateLimitService;
import com.example.rednote.auth.common.tool.KeysUtil;
import com.example.rednote.auth.common.tool.RedisUtil;

// === Swagger / OpenAPI 3 注解 ===
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;

/**
 * 限流管理接口（管理端）
 * - 动态覆盖令牌桶参数：容量(cap)、补充速率(rate/秒)、空闲过期时间(idleTtl/秒)
 * - 黑名单管理：用户 / IP 支持 TTL 拉黑与解除
 */
@RestController
@RequestMapping("/admin/ratelimit")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "限流管理", description = "动态调整令牌桶与黑名单（Redis/Cluster）")
// 如果你的 OpenAPI 安全方案名称不同，请把 bearerAuth 改成你的名称
@SecurityRequirement(name = "bearerAuth")
public class AdminRateLimitController {

  private final RateLimitService svc;
  private final RedisUtil redisUtil;

  // ---------------- 用户维度：令牌桶配置 ----------------

  /**
   * 覆盖指定用户的令牌桶参数（为空的字段不修改）
   */
  @Operation(
      summary = "设置用户令牌桶参数",
      description = "覆盖用户的桶容量(cap)、令牌速率(rate/秒)、空闲过期时间(idleTtl/秒)。未提供的字段保持不变。")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "更新成功"),
      @ApiResponse(responseCode = "400", description = "请求参数错误", content = @Content),
      @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
      @ApiResponse(responseCode = "403", description = "无权限", content = @Content)
  })

  @PostMapping("/users/{userId}")
  public ResponseEntity<?> setUser(
      @Parameter(description = "用户ID", required = true)
      @PathVariable long userId,
      @org.springframework.web.bind.annotation.RequestBody
      ConfReq req
  ) {
    // 为空的字段不修改（服务层已做判空）
    svc.setUserConf(userId, req.cap, req.rate, req.idleTtl);
    return ResponseEntity.ok().build();
  }

  /**
   * 清除用户的覆盖配置（回退到默认参数）
   */
  @Operation(summary = "清空用户令牌桶覆盖配置", description = "删除该用户的令牌桶配置，使之回退到全局默认值")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "清除成功"),
      @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
      @ApiResponse(responseCode = "403", description = "无权限", content = @Content)
  })
  @DeleteMapping("/users/{userId}")
  public ResponseEntity<?> clearUser(
      @Parameter(description = "用户ID", required = true)
      @PathVariable long userId
  ) {
    svc.clearUserConf(userId);
    return ResponseEntity.ok().build();
  }


  @GetMapping("/users/{userId}")
  public ResponseEntity<?> statusUser(
      @Parameter(description = "用户ID", required = true)
      @PathVariable long userId
  ) {
    return ResponseEntity.ok().body( redisUtil.gTemplate().opsForHash().get(KeysUtil.confKeyByUser(2), "cap"));
  }
  
  // ---------------- 用户维度：黑名单 ----------------

  /**
   * 将用户拉黑（可选 TTL，单位：秒；不传或 <=0 表示永久）
   */
  @Operation(summary = "拉黑用户", description = "将用户加入黑名单，支持设置过期时间（秒）。不传/≤0 表示永久拉黑。")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "已拉黑"),
      @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
      @ApiResponse(responseCode = "403", description = "无权限", content = @Content)
  })
  @PostMapping("/blacklist/users/{userId}")
  public ResponseEntity<?> blUser(
      @Parameter(description = "用户ID", required = true)
      @PathVariable long userId,
      @Parameter(description = "拉黑的存活时间（秒），可选；不传或 ≤0 表示永久")
      @RequestParam(required = false) Integer ttlSec
  ) {
    svc.blacklistUser(userId, ttlSec);
    return ResponseEntity.ok().build();
  }

  /**
   * 将用户移出黑名单
   */
  @Operation(summary = "解黑用户", description = "将用户从黑名单中移除")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "已移除"),
      @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
      @ApiResponse(responseCode = "403", description = "无权限", content = @Content)
  })
  @DeleteMapping("/blacklist/users/{userId}")
  public ResponseEntity<?> unblUser(
      @Parameter(description = "用户ID", required = true)
      @PathVariable long userId
  ) {
    svc.unblacklistUser(userId);
    return ResponseEntity.ok().build();
  }

  // ---------------- IP 维度：令牌桶配置 ----------------

  /**
   * 覆盖指定 IP 的令牌桶参数（为空的字段不修改）
   */
  @Operation(
      summary = "设置IP令牌桶参数",
      description = "覆盖 IP 的桶容量(cap)、令牌速率(rate/秒)、空闲过期时间(idleTtl/秒)。未提供的字段保持不变。")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "更新成功"),
      @ApiResponse(responseCode = "400", description = "请求参数错误", content = @Content),
      @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
      @ApiResponse(responseCode = "403", description = "无权限", content = @Content)
  })
  @PutMapping("/ips/{ip}")
  public ResponseEntity<?> setIp(
      @Parameter(description = "目标IP地址", required = true, example = "1.2.3.4")
      @PathVariable String ip,
      @org.springframework.web.bind.annotation.RequestBody
      ConfReq req
  ) {
    svc.setIpConf(ip, req.cap, req.rate, req.idleTtl);
    return ResponseEntity.ok().build();
  }

  /**
   * 清除 IP 的覆盖配置（回退到默认参数）
   */
  @Operation(summary = "清空IP令牌桶覆盖配置", description = "删除该 IP 的令牌桶配置，使之回退到全局默认值")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "清除成功"),
      @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
      @ApiResponse(responseCode = "403", description = "无权限", content = @Content)
  })
  @DeleteMapping("/ips/{ip}")
  public ResponseEntity<?> clearIp(
      @Parameter(description = "目标IP地址", required = true, example = "1.2.3.4")
      @PathVariable String ip
  ) {
    svc.clearIpConf(ip);
    return ResponseEntity.ok().build();
  }

  // ---------------- IP 维度：黑名单 ----------------

  /**
   * 将 IP 拉黑（可选 TTL，单位：秒；不传或 <=0 表示永久）
   */
  @Operation(summary = "拉黑IP", description = "将 IP 加入黑名单，支持设置过期时间（秒）。不传/≤0 表示永久拉黑。")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "已拉黑"),
      @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
      @ApiResponse(responseCode = "403", description = "无权限", content = @Content)
  })
  @PostMapping("/blacklist/ips/{ip}")
  public ResponseEntity<?> blIp(
      @Parameter(description = "目标IP地址", required = true, example = "1.2.3.4")
      @PathVariable String ip,
      @Parameter(description = "拉黑的存活时间（秒），可选；不传或 ≤0 表示永久")
      @RequestParam(required = false) Integer ttlSec
  ) {
    svc.blacklistIp(ip, ttlSec);
    return ResponseEntity.ok().build();
  }

  /**
   * 将 IP 移出黑名单
   */
  @Operation(summary = "解黑IP", description = "将 IP 从黑名单中移除")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "已移除"),
      @ApiResponse(responseCode = "401", description = "未认证", content = @Content),
      @ApiResponse(responseCode = "403", description = "无权限", content = @Content)
  })
  @DeleteMapping("/blacklist/ips/{ip}")
  public ResponseEntity<?> unblIp(
      @Parameter(description = "目标IP地址", required = true, example = "1.2.3.4")
      @PathVariable String ip
  ) {
    svc.unblacklistIp(ip);
    return ResponseEntity.ok().build();
  }

  // ================= DTO =================

  /**
   * 令牌桶覆盖配置请求体
   * - 任何字段为 null 表示“不修改该项”，仅覆盖非空字段
   */
  @Data
  public static class ConfReq {
    @Schema(description = "桶容量（cap）", example = "200")
    Integer cap;

    @Schema(description = "令牌速率（每秒生成的令牌数，rate/s）", example = "80")
    Integer rate;

    @Schema(description = "桶在空闲时的过期时间（秒，idle_ttl）", example = "900")
    Integer idleTtl;
  }
}