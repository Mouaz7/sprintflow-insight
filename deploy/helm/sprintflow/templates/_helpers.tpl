{{- define "sprintflow.name" -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "sprintflow.selector" -}}
app.kubernetes.io/name: sprintflow
app.kubernetes.io/instance: {{ .Release.Name | quote }}
{{- end -}}

{{- define "sprintflow.image" -}}
{{- if .Values.image.digest -}}
{{ .Values.image.repository }}@{{ .Values.image.digest }}
{{- else -}}
{{ .Values.image.repository }}:{{ required "set image.tag or image.digest" .Values.image.tag }}
{{- end -}}
{{- end -}}
