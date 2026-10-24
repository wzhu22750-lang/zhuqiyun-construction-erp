import {reactive,watch,computed} from 'vue'
import {useResource} from './useResource'
import type {Row} from '../api/types'
export function usePagedResource(path:string|(()=>string)) {
 const paging=reactive({page:1,size:20,total:0,q:''})
 const basePath=computed(()=>typeof path==='function'?path():path)
 watch(basePath,()=>{paging.page=1})
 const resource=useResource<{items:Row[];total:number}>(()=>{const base=basePath.value;return base?`${base}${base.includes('?')?'&':'?'}page=${paging.page}&size=${paging.size}&q=${encodeURIComponent(paging.q)}`:''},{items:[],total:0})
 watch(()=>resource.data.value.total,v=>paging.total=v)
 const onPage=(v:number)=>paging.page=v
 const onSize=(v:number)=>{paging.size=v;paging.page=1}
 const onSearch=(v:string)=>{paging.q=v;paging.page=1}
 return {...resource,data:computed(()=>resource.data.value.items),paging,onPage,onSize,onSearch}
}
