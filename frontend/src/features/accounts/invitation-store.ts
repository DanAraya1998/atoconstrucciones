import { useEffect, useMemo, useSyncExternalStore } from "react";
export type PendingInvitation = {id:string;token:string;expiresAt:string};
const storageKey="ato.pendingInvitation", eventName="ato:invitation";
let fallback="";
function parse(raw:string):PendingInvitation|null {
  try{const data=JSON.parse(raw) as Partial<PendingInvitation>;if(typeof data.id!=="string"||! /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(data.id)||typeof data.token!=="string"||! /^[A-Za-z0-9_-]{32,100}$/.test(data.token)||typeof data.expiresAt!=="string"||!Number.isFinite(Date.parse(data.expiresAt))||Date.parse(data.expiresAt)<=Date.now())return null;return {id:data.id,token:data.token,expiresAt:data.expiresAt};}catch{return null;}
}
function fragment():string {
  if(window.location.pathname!=="/invitacion"||!window.location.hash)return "";
  if(window.location.hash.length>1024)return "{}";
  const params=new URLSearchParams(window.location.hash.slice(1));return JSON.stringify({id:params.get("id"),token:params.get("token"),expiresAt:params.get("expiresAt")});
}
function snapshot():string {const hash=fragment();if(hash)return hash;try{return window.sessionStorage.getItem(storageKey)??fallback;}catch{return fallback;}}
function captureFragment(){const hash=fragment();if(!hash)return;const valid=parse(hash);if(!valid)return;fallback=JSON.stringify(valid);try{window.sessionStorage.setItem(storageKey,fallback);}catch{/* La memoria conserva el enlace mientras se permanece en la página. */}window.history.replaceState(null,"",window.location.pathname);}
function subscribe(listener:()=>void){const navigation=()=>{captureFragment();listener();};window.addEventListener(eventName,listener);window.addEventListener("hashchange",navigation);window.addEventListener("popstate",navigation);return ()=>{window.removeEventListener(eventName,listener);window.removeEventListener("hashchange",navigation);window.removeEventListener("popstate",navigation);};}
export function clearInvitation(){fallback="";try{window.sessionStorage.removeItem(storageKey);}catch{/* La memoria se limpia también si el navegador bloquea storage. */}if(window.location.pathname==="/invitacion")window.history.replaceState(null,"",window.location.pathname);window.dispatchEvent(new Event(eventName));}
export function usePendingInvitation(){
  const raw=useSyncExternalStore(subscribe,snapshot,()=>"");const pending=useMemo(()=>parse(raw),[raw]);
  useEffect(()=>{if(parse(fragment())){captureFragment();window.dispatchEvent(new Event(eventName));}},[raw]);
  useEffect(()=>{if(!pending)return;const timer=window.setTimeout(clearInvitation,Math.min(Date.parse(pending.expiresAt)-Date.now(),86400000));return ()=>window.clearTimeout(timer);},[pending]);
  return {pending,invalid:Boolean(raw&&!pending)};
}
