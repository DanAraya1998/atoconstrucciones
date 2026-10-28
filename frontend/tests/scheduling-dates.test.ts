import { test } from "node:test";
import assert from "node:assert/strict";
import { addCivilDays, civilDaysBetween, fromCivil, toCivil } from "../src/features/scheduling/types.ts";

test("reprogramación conserva días civiles en cambios de mes, año y año bisiesto", () => {
  assert.equal(addCivilDays("2028-02-28",2),"2028-03-01");
  assert.equal(civilDaysBetween("2028-02-28","2028-03-01"),2);
  assert.equal(addCivilDays("2026-12-31",1),"2027-01-01");
  assert.equal(addCivilDays("2026-01-01",-1),"2025-12-31");
  assert.equal(toCivil(fromCivil("2026-10-27")),"2026-10-27");
});
test("duración positiva y fechas inválidas no se convierten en días distintos", () => {
  assert.equal(civilDaysBetween("2026-10-27","2026-10-28"),1);
  assert.equal(civilDaysBetween("2026-10-27","2026-10-27"),0);
  assert.equal(addCivilDays("2026-02-30",1),"");
  assert.equal(addCivilDays("",1),"");
  assert.equal(addCivilDays("2026-10-27",NaN),"");
  assert.equal(addCivilDays("9999-12-31",1),"");
});
