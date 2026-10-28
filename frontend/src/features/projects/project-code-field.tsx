"use client";

import { useId, useState } from "react";

export function normalizeProjectCode(value: string) {
  return value.trim().toUpperCase().replace(/_/g, "-");
}

export function ProjectCodeField() {
  const id = useId();
  const [value, setValue] = useState("");
  const [touched, setTouched] = useState(false);
  const code = normalizeProjectCode(value);
  const valid = /^[A-Z0-9-]{3,30}$/.test(code);
  const invalid = touched && !valid;
  const message = "Escribe un código de 3 a 30 caracteres, con letras A-Z, números y guiones (-).";

  return <div className="grid min-w-0 gap-2">
    <label htmlFor={`${id}-code`}>Código de obra</label>
    <input id={`${id}-code`} name="code" required minLength={3} maxLength={30}
      pattern="\s*[A-Za-z0-9_\-]{3,30}\s*" title={message} placeholder="OBRA-001"
      autoComplete="off" autoCapitalize="characters" spellCheck={false} value={value}
      aria-invalid={invalid || undefined}
      aria-describedby={`${id}-help ${id}-preview${invalid ? ` ${id}-error` : ""}`}
      onChange={event => setValue(event.target.value)}
      onBlur={() => { setValue(code); setTouched(Boolean(value)); }}
      onInvalid={() => setTouched(true)} />
    <p id={`${id}-help`} className="text-sm text-slate-600">De 3 a 30 caracteres. Convertimos minúsculas a mayúsculas y guiones bajos (_) a guiones (-).</p>
    <p id={`${id}-preview`} className="text-sm">{valid ? <>Se guardará como <strong>{code}</strong>.</> : "Ejemplo: OBRA-001."}</p>
    {invalid && <p id={`${id}-error`} role="alert" className="text-sm">{message}</p>}
  </div>;
}
