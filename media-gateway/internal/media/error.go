package media

import "errors"

// Reason 返回可枚举原因；未知探测异常使用统一失败码，不回传其原始内容。
func Reason(err error) string {
	return ReasonOr(err, "SOURCE_PROBE_FAILED")
}

// ReasonOr 保留具体边界返回的原因码，未知异常使用调用方所属阶段的默认码。
func ReasonOr(err error, fallback string) string {
	var failure *Failure
	if errors.As(err, &failure) {
		return failure.Code
	}
	return fallback
}
