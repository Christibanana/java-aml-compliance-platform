import {defineStore} from 'pinia';
import {http} from '../api/http';

function storedList(key:string):string[]{
  try{return JSON.parse(localStorage.getItem(key)||'[]')}catch{return []}
}

export const useAuthStore=defineStore('auth',{
  state:()=>({
    token:localStorage.getItem('aml_token')||'',
    displayName:localStorage.getItem('aml_name')||'',
    roles:storedList('aml_roles'),
    permissions:storedList('aml_permissions')
  }),
  actions:{
    async login(username:string,password:string){
      const {data}=await http.post('/auth/login',{username,password});
      this.token=data.token;this.displayName=data.displayName;this.roles=data.roles||[];this.permissions=data.permissions||[];
      localStorage.setItem('aml_token',data.token);localStorage.setItem('aml_name',data.displayName);
      localStorage.setItem('aml_roles',JSON.stringify(this.roles));
      localStorage.setItem('aml_permissions',JSON.stringify(this.permissions));
    },
    has(permission:string){return this.permissions.includes(permission)},
    logout(){
      this.token='';this.displayName='';this.roles=[];this.permissions=[];
      ['aml_token','aml_name','aml_roles','aml_permissions'].forEach(key=>localStorage.removeItem(key));
      location.href='/login';
    }
  }
});
