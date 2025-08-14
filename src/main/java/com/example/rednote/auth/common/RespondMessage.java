package com.example.rednote.auth.common;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.ToString;



@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class RespondMessage<T> {
    private String message;
    private int code;
    private T data;

 // 静态方法便于快速返回成功或失败结果
    public static <T> RespondMessage<T> success(T data) {
        return new RespondMessage<>("请求成功", 200, data);
    }
    public static <T> RespondMessage<T> success(String message) {
        return new RespondMessage<>(message, 200, null);
    }


    public static <T> RespondMessage<T> success(String message, T data) {
        return new RespondMessage<>(message, 200, data);
    }

    public static <T> RespondMessage<T> fail(String message, int status) {
        return new RespondMessage<>(message, status, null);
    }

    public static <T> RespondMessage<T> fail(String message) {
        return new RespondMessage<>(message, 400, null);
    }

    
}
