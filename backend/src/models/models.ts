type StatusTable = 'Preparation' | 'Opened' | 'In process' | 'Pause' | 'Canceled' | 'Finished' | 'Deleted';
type StatusCatalogues = 'Created' | 'Allowed' | 'Rejected' | 'Deleted';

export type Systems = {
    id: string
    name: string
    status?: StatusCatalogues
    system_id?: string
};

export type Tags = {
    id: string
    name: string
    status?: StatusCatalogues
    tag_id?: string
};

export type Platforms = {
    id: string
    name: string
    status?: StatusCatalogues
    platform_id?: string
};

export type Users = {
    id?: string
    discord_name: string
    name: string
    karma: number
    timezone: string
    country: string
}

export type Table = {
    id?: string
    name: string
    description: string | null
    permitted: string | null
    startdate: any | null
    timezone: string | null
    requeriments: string | null
    status?: StatusTable
    duration: string | null
    systems?: Systems[] | null
    platforms?: Platforms[] | null
    tags?: Tags[] | null
    players?: Users[] | null
    schedule?: Schedules[] | null
    masters?:  Masters[]
}

export type Schedules = {
    day : string,
    hour : string[]
}

export type ResponseModel = {
    status: number
    response: string
}

export type Generalview = {
    public_tables: {} | null
    joined_tables: {} | null
    request_tables: {} | null
}

export type GeneralMasterView = {
    owner_tables: {} | null,
    master_tables: {} | null,
    request_tables: {} | null,
}

export type Masters = {
    id: string,
    username: string,
    master_type?: string
}

export type Schedule = {
    day: string,
    hour: string
}

export type MastersList = {
    users_master: Masters[] | null
}

export type TableMasterList = {
    table_list: {} | null
}