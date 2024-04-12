type StatusTable = 'Preparation' | 'Opened' | 'In process' | 'Pause' | 'Canceled' | 'Finished' | 'Deleted';
type StatusCatalogues = 'Created' | 'Allowed' | 'Rejected' | 'Deleted';

export interface Systems {
    id: string
    name: string
    status?: StatusCatalogues
    system_id?: string
};

export interface Tags {
    id: string
    name: string
    status?: StatusCatalogues
    tag_id?: string
};

export interface Platforms {
    id: string
    name: string
    status?: StatusCatalogues
    platform_id?: string
};

export interface Users {
    id?: string
    discord_name: string
    name: string
    karma: number
    timezone: string
    country: string
}

export interface Table {
    id?: string
    name: string
    description: string | null
    permitted: string | null
    startdate: any | null
    timezone: string | null
    requeriments: string | null
    status?: StatusTable
    duration: string
    systems?: Systems[] | []
    platforms?: Platforms[] | []
    tags?: Tags[] | []
    players?: Users[] | []
}

export interface ResponseModel {
    status: number
    response: string
}

export interface Generalview {
    public_tables: {} | null
    joined_tables: {} | null
    request_tables: {} | null
}

export interface GeneralMasterView {
    owner_tables: {} | null,
    master_tables: {} | null,
    request_tables: {} | null,
}

export interface Masters {
    users_master: {} | null
}
