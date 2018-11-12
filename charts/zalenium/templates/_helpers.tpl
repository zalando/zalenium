{{/* vim: set filetype=mustache: */}}
{{/*
Expand the name of the chart.
*/}}
{{- define "zalenium.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "zalenium.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/*
Create chart name and version as used by the chart label.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
*/}}
{{- define "zalenium.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Create the name of the service account
*/}}
{{- define "zalenium.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
    {{ default (include "zalenium.fullname" .) .Values.serviceAccount.name }}
{{- else -}}
    {{ default "default" .Values.serviceAccount.name }}
{{- end -}}
{{- end -}}

{{/*
Create base64 encoded basic authentication for readiness/liveness probe
*/}}
{{- define "basicAuth.b64" -}}
{{- printf "%s:%s" .Values.hub.basicAuth.username .Values.hub.basicAuth.password | b64enc -}}
{{- end -}}

{{/*  Manage the labels for each entity  */}}
{{- define "zalenium.labels" -}}
role: grid
{{- /* Old labels */}}
app: {{ template "zalenium.name" . }}
chart: {{ template "zalenium.chart" . }}
release: {{ .Release.Name }}
heritage: {{ .Release.Service }}
{{- /* New labels */}}
app.kubernetes.io/name: {{ include "zalenium.name" . }}
helm.sh/chart: {{ include "zalenium.chart" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}