import Dashboard from './lib/pages/Dashboard.svelte'
import ResourceList from './lib/pages/ResourceList.svelte'
import ResourceDetail from './lib/pages/ResourceDetail.svelte'
import ResourceCreate from './lib/pages/ResourceCreate.svelte'
import NotFound from './lib/pages/NotFound.svelte'
import Login from './lib/components/Login.svelte'
import LogsView from './lib/pages/LogsView.svelte'
import Settings from './lib/pages/Settings.svelte'
import Analytics from './lib/pages/Analytics.svelte'
import Telemetry from './lib/pages/Telemetry.svelte'

export const routes = {
    '/': Dashboard,
    '/resources/:name': ResourceList,
    '/resources/:name/create': ResourceCreate,
    '/resources/:name/edit/:id': ResourceCreate,
    '/resources/:name/:id': ResourceDetail,
    '/auth/login': Login,
    "/logs": LogsView, 
    "/settings": Settings,
    "/analytics": Analytics,
    "/telemetry": Telemetry,
    '*': NotFound,
}