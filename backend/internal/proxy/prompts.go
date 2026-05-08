package proxy

import "fmt"

func TranslationPrompt(sourceLang, targetLang, tone string) string {
	return fmt.Sprintf(`Translate %s to %s (%s tone). Rewrite naturally. Output ONLY the final text.`, sourceLang, targetLang, tone)
}

func FixTextPrompt() string {
	return `Fix grammar/spelling. Improve flow. Output ONLY the final text. If perfect, return as is.`
}

// Keeping this minimal
func languageDesc(code string) string {
	return code
}
