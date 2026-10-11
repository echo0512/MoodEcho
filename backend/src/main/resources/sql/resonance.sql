-- 仅用于全新安装；已有数据库请使用 migrations 中的增量脚本。

CREATE DATABASE moodecho_app
DEFAULT CHARACTER SET utf8mb4 #这个字符集支持中文和emoji
COLLATE utf8mb4_unicode_ci;

USE moodecho_app;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',

    email VARCHAR(100) NOT NULL UNIQUE COMMENT '邮箱，作为登录账号',

    password_hash VARCHAR(255) NOT NULL COMMENT '加密后的密码',

    nickname VARCHAR(50) DEFAULT NULL COMMENT '昵称，注册后系统自动生成，用户后续可修改',

    avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像地址',

    status TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：1正常，0禁用',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE email_verification_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '验证码ID',

    email VARCHAR(100) NOT NULL COMMENT '接收验证码的邮箱',

    code VARCHAR(10) NOT NULL COMMENT '验证码',

    purpose VARCHAR(30) NOT NULL COMMENT '验证码用途：REGISTER 注册',

    used TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用：0未使用，1已使用',

    expire_time DATETIME NOT NULL COMMENT '过期时间',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    used_at DATETIME DEFAULT NULL COMMENT '使用时间',

    INDEX idx_email_purpose_created_at (email, purpose, created_at),
    INDEX idx_email_purpose_used (email, purpose, used)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱验证码表';

CREATE TABLE diary (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日记ID',

    user_id BIGINT NOT NULL COMMENT '所属用户ID',

    title VARCHAR(50) DEFAULT NULL COMMENT '日记标题，可选，最多50字',

    content TEXT NOT NULL COMMENT '日记正文，最多10000字',

    record_date DATE NOT NULL COMMENT '记录日期，表示这篇日记记录的是哪一天',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0未删除，1已删除',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_user_deleted_record_date (user_id, deleted, record_date),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私密日记表';

CREATE TABLE diary_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日记图片ID',

    diary_id BIGINT NOT NULL COMMENT '所属日记ID',

    user_id BIGINT NOT NULL COMMENT '所属用户ID，方便权限校验',

    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',

    stored_name VARCHAR(255) NOT NULL COMMENT '服务器保存后的文件名',

    content_type VARCHAR(100) NOT NULL COMMENT '文件类型，如 image/jpeg',

    file_size BIGINT NOT NULL COMMENT '文件大小，单位字节',

    storage_path VARCHAR(500) NOT NULL COMMENT '本地存储路径',

    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0未删除，1已删除',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    INDEX idx_diary_id (diary_id),
    INDEX idx_user_id (user_id),
    INDEX idx_diary_deleted (diary_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记图片表';

ALTER TABLE diary
ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '日记状态：0草稿，1正式'
AFTER deleted;

CREATE TABLE diary_embedding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日记向量ID',

    diary_id BIGINT NOT NULL COMMENT '关联的日记ID',

    user_id BIGINT NOT NULL COMMENT '所属用户ID，用于权限隔离',

    embedding LONGTEXT NOT NULL COMMENT '版本化向量快照，含整篇向量和片段向量JSON',

    model_name VARCHAR(100) DEFAULT NULL COMMENT '向量模型名称',

    content_hash VARCHAR(64) NOT NULL COMMENT '日记清洗后正文的哈希值，用于判断内容是否变化',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',

    UNIQUE KEY uk_diary_id (diary_id),

    INDEX idx_user_id (user_id),

    INDEX idx_user_diary (user_id, diary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记语义向量表';

CREATE TABLE IF NOT EXISTS resonance_topic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL COMMENT '话题名称',
    description VARCHAR(255) NULL COMMENT '话题描述',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1启用，0停用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共鸣固定话题表';

INSERT INTO resonance_topic (id, name, description, sort_order, enabled)
VALUES
    (1, '学习压力', '关于考试、作业、ddl、复习和成长压力', 1, 1),
    (2, '关系与相处', '关于朋友、同学、伴侣和人际边界', 2, 1),
    (3, '家庭小事', '关于家人、日常照顾和偶尔的小摩擦', 3, 1),
    (4, '自我怀疑', '关于不确定、不自信和对自己的反复确认', 4, 1),
    (5, '深夜情绪', '关于夜晚突然涌上来的难过、想念和疲惫', 5, 1),
    (6, '小小开心', '关于今天发生的一点点快乐和被照亮的瞬间', 6, 1),
    (7, '生活碎片', '关于普通日子里的细节、观察和片段', 7, 1),
    (8, '迷茫时刻', '关于选择、未来、方向感和暂时看不清的路', 8, 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled);

CREATE TABLE IF NOT EXISTS resonance_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '投递用户ID',
    diary_id BIGINT NULL COMMENT '来源日记ID，可为空',
    topic_id BIGINT NULL COMMENT '话题ID，可为空',
    content TEXT NOT NULL COMMENT '匿名投递纯文本内容',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1已发布，2已撤回',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    withdrawn_at DATETIME NULL COMMENT '撤回时间',
    INDEX idx_resonance_post_user_id (user_id),
    INDEX idx_resonance_post_status (status),
    INDEX idx_resonance_post_diary_id (diary_id),
    INDEX idx_resonance_post_topic_id (topic_id),
    INDEX idx_resonance_post_topic_status (topic_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匿名共鸣投递表';

-- Existing installations: run against moodecho_app before starting the updated backend.
-- Additive and repeatable. No existing diary or vector data is changed.
CREATE TABLE IF NOT EXISTS diary_embedding_job (
    diary_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    state VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_until DATETIME(6) NULL,
    claim_token VARCHAR(36) NULL,
    error_code VARCHAR(40) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_embedding_job_due (state, next_attempt_at),
    INDEX idx_embedding_job_lease (state, lease_until),
    INDEX idx_embedding_job_user (user_id),
    CONSTRAINT fk_embedding_job_diary FOREIGN KEY (diary_id) REFERENCES diary(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日记语义索引后台任务';
