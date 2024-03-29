CREATE TABLE IF NOT EXISTS Users (
    id VARCHAR(64),
    discord_username VARCHAR(64),
    name VARCHAR(64),
    karma INT DEFAULT 8000,
    timezone VARCHAR(64),
    country VARCHAR(64),
    status ENUM ('Allowed', 'Blocked') DEFAULT 'Allowed',
    CONSTRAINT PK_Users PRIMARY KEY (id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Roles (
    id VARCHAR(64),
    name VARCHAR(16),
    CONSTRAINT PK_Roles PRIMARY KEY (id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Users_Roles (
    user_id VARCHAR(64),
    rol_id VARCHAR(64),
    status ENUM ('Allowed', 'Deleted') DEFAULT 'Allowed',
    CONSTRAINT PK_Users_Roles PRIMARY KEY (user_id, rol_id),
    FOREIGN KEY (user_id) REFERENCES Users(id),
    FOREIGN KEY (rol_id) REFERENCES Roles(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Comments (
    id VARCHAR(64),
    description VARCHAR(1024),
    comment_type ENUM ('JJ', 'JM', 'MJ', 'General'),
    karma_impact ENUM ('Commented', 'Neutral', 'Reported'), 
    status ENUM ('Under review', 'Reviewed', 'Rejected', 'Deleted') DEFAULT 'Under review',
    user_created_id VARCHAR(64),
    user_commented_id VARCHAR(64) NULL,
    user_reviewed_id VARCHAR(64) NULL,
    CONSTRAINT PK_Comments PRIMARY KEY (id),
    FOREIGN KEY (user_created_id) REFERENCES Users(id),
    FOREIGN KEY (user_commented_id) REFERENCES Users(id),
    FOREIGN KEY (user_reviewed_id) REFERENCES Users(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Requests (
    id VARCHAR(64),
    justification VARCHAR(1024),
    status ENUM ('Under review', 'Reviewed', 'Rejected', 'Deleted') DEFAULT 'Under review',
    request_type ENUM ('Rol', 'Table', 'Master'),
    user_created_id VARCHAR(64),
    user_reviewed_id VARCHAR(64) NULL,
    CONSTRAINT PK_Requests PRIMARY KEY (id),
    FOREIGN KEY (user_created_id) REFERENCES Users(id),
    FOREIGN KEY (user_reviewed_id) REFERENCES Users(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Files (
    id VARCHAR(64),
    name VARCHAR(64),
    source VARCHAR(256),
    mine VARCHAR(8),
    status ENUM ('Current', 'Deleted') DEFAULT 'Current',
    user_created_id VARCHAR(64),
    CONSTRAINT PK_Files PRIMARY KEY (id),
    FOREIGN KEY (user_created_id) REFERENCES Users(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Tables (
    id VARCHAR(64),
    name VARCHAR(32),
    description VARCHAR(1024) NULL,
    permitted VARCHAR(1024) NULL,
    startdate DATETIME NULL,
    timezone VARCHAR(64) NULL,
    requeriments VARCHAR(1024) NULL,
    status ENUM ('Preparation', 'Opened', 'In process', 'Pause', 'Canceled', 'Finished', 'Deleted') DEFAULT 'Preparation',
    table_type ENUM('Public', 'First class') DEFAULT 'Public',
    duration TIME,
    created_at DATETIME,
    CONSTRAINT PK_Table PRIMARY KEY (id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Users_registration (
    id VARCHAR(64),
    status ENUM ('Candidate', 'Player', 'Rejected', 'Blocked', 'Deleted') DEFAULT 'Candidate',
    description VARCHAR(1024) NULL,
    table_id VARCHAR(64),
    user_id VARCHAR(64),
    CONSTRAINT PK_Registration PRIMARY KEY (id),
    FOREIGN KEY (table_id) REFERENCES Tables(id),
    FOREIGN KEY (user_id) REFERENCES Users(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Registration_Files (
    joined_id VARCHAR(64),
    file_id VARCHAR(64),
    CONSTRAINT PK_files_registration PRIMARY KEY (joined_id, file_id),
    FOREIGN KEY (joined_id) REFERENCES Users_registration(id),
    FOREIGN KEY (file_id) REFERENCES Files(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Table_Files (
    table_id VARCHAR(64),
    file_id VARCHAR(64),
    table_file_type ENUM ('Preparation', 'Sesion', 'Deleted'),
    CONSTRAINT PK_files_table PRIMARY KEY (table_id, file_id),
    FOREIGN KEY (table_id) REFERENCES Tables(id),
    FOREIGN KEY (file_id) REFERENCES Files(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Days (
    table_id VARCHAR(64),
    status ENUM ('Created', 'Deleted'),
    hour TIME,
    day ENUM ('Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'),
    CONSTRAINT PK_Days PRIMARY KEY (table_id),
    FOREIGN KEY (table_id) REFERENCES Tables(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Platforms (
    id VARCHAR(64),
    name VARCHAR(128),
    status ENUM ('Created', 'Allowed', 'Rejected', 'Deleted') DEFAULT 'Created',
    platform_id VARCHAR(64) NULL,
    CONSTRAINT PK_Platforms PRIMARY KEY Platforms(id),
    CONSTRAINT UC_Platforms_name UNIQUE(name)
)ENGINE=InnoDB;


CREATE TABLE IF NOT EXISTS Tags (
    id VARCHAR(64),
    name VARCHAR(128),
    status ENUM ('Created', 'Allowed', 'Rejected', 'Deleted') DEFAULT 'Created',
    tag_id VARCHAR(64) NULL,
    CONSTRAINT PK_Tags PRIMARY KEY Tags(id),
    CONSTRAINT UC_Tags_name UNIQUE(name)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Systems (
    id VARCHAR(64),
    name VARCHAR(128),
    status ENUM ('Created', 'Allowed', 'Rejected', 'Deleted') DEFAULT 'Created',
    system_id VARCHAR(64) NULL,
    CONSTRAINT PK_Systems PRIMARY KEY Systems(id),
    CONSTRAINT UC_Systems_name UNIQUE(name)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Table_Platforms (
    table_id VARCHAR(64),
    platform_id VARCHAR(64),
    status ENUM ('Used', 'Deleted'),
    CONSTRAINT PK_Catalogue_Platform PRIMARY KEY (table_id, platform_id),
    FOREIGN KEY (table_id) REFERENCES Tables(id),
    FOREIGN KEY (platform_id) REFERENCES Platforms(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Table_Tags (
    table_id VARCHAR(64),
    tag_id VARCHAR(64),
    status ENUM ('Used', 'Deleted'),
    CONSTRAINT PK_Catalogue_Tag PRIMARY KEY (table_id, tag_id),
    FOREIGN KEY (table_id) REFERENCES Tables(id),
    FOREIGN KEY (tag_id) REFERENCES Tags(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Table_Systems (
    table_id VARCHAR(64),
    system_id VARCHAR(64),
    status ENUM ('Used', 'Deleted'),
    CONSTRAINT PK_Catalogue_Systems PRIMARY KEY (table_id, system_id),
    FOREIGN KEY (table_id) REFERENCES Tables(id),
    FOREIGN KEY (system_id) REFERENCES Systems(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Masters (
    table_id VARCHAR(64),
    user_master_id VARCHAR(64),
    master_type ENUM ('Owner', 'Master') DEFAULT 'Master',
    status ENUM ('Created', 'Deleted') DEFAULT 'Created',
    CONSTRAINT PK_Master PRIMARY KEY (table_id, user_master_id),
    FOREIGN KEY (table_id) REFERENCES Tables(id),
    FOREIGN KEY (user_master_id) REFERENCES Users(id)
)ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Logs (
    id VARCHAR(64),
    entity_type VARCHAR(32),
    entity_id VARCHAR(64),
    updated_at TIMESTAMP,
    updated_by VARCHAR(64),
    before_data JSON NULL,
    after_data JSON
)ENGINE=InnoDB;