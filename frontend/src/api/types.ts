export type Id = number | string
export type Decimal = string | number
// Extended joined fields are preserved as supplied by each endpoint.
export interface Row { id: Id; [key: string]: any }
export interface User extends Row { username: string; name: string; role: string; permissions: string[]; menus: string[] }
export interface Company { name: string; system_name: string; theme_color: string }
export interface Bootstrap { user: User; company: Company; projects: Row[]; materials: Row[]; suppliers: Row[]; warehouses: Row[]; users: Row[]; dictionaries: Record<string, Row[]> }
export interface Envelope<T> { code: number; data: T; msg: string }
export interface LineItem { id?: Id; material_id: Id | ''; quantity: Decimal; unit_price: Decimal; material_name?: string; specification?: string; unit?: string; amount?: Decimal; received_quantity?: Decimal }
export interface RequestPayload { project_id: Id | ''; required_date: string; note: string; items: LineItem[] }
export interface PurchaseRequest extends Row { project_id: Id; applicant_id: Id; status: string; total_amount: Decimal; items: (Row & LineItem)[]; quotes: Row[]; approvals: Row[] }
export interface Order extends Row { items: Row[]; receipts: Row[]; payments: Row[] }
export interface Dashboard { metrics: Record<string, Decimal>; budget_vs_cost: {name:string;budget:Decimal;cost:Decimal}[]; monthly_purchases: {month:string;amount:Decimal}[]; cost_composition: {name:string;value:Decimal}[]; project_progress: {name:string;progress:Decimal}[]; order_status: {name:string;value:Decimal}[]; todos: Row[]; recent_activity: Row[] }
