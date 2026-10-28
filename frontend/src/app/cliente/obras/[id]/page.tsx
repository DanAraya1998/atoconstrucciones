import { ClientProject } from "@/features/client/client-workspace";
export default async function Page({params}:{params:Promise<{id:string}>}){return <ClientProject id={(await params).id}/>;}
