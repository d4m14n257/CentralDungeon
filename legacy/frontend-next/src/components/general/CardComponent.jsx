import { Card, CardHeader, IconButton, Box, Typography, CardContent, Collapse, Stack, Tooltip, Divider } from '@mui/material';

import ExpandLess from '@mui/icons-material/ExpandLess';
import ExpandMore from '@mui/icons-material/ExpandMore';

import { global } from '@/styles/global';
import { useState } from 'react';

const useCardComponent = () => {
    const [open, setOpen] = useState(true);

    const handleChange = () => {
        setOpen(!open)
    }

    return {
        open,
        handleChange
    }
}

export default function CardComponent (props) {
    const { title, subtitle, Actions, children, handleAction, data, hasCollapse, titleAction, IconAction } = props;
    const { open, handleChange } = useCardComponent();

    return (
        <Card sx={{...global.border, padding: 2}}>
            <CardHeader
                title={title}
                subheader={subtitle}
                action={
                    <Stack direction='row' spacing={0.5}>
                        {Actions &&
                            <Actions 
                                handleAction={handleAction}
                                data={data}
                                title={titleAction}
                                IconAction={IconAction}
                            />
                        }
                        {hasCollapse &&
                            <Tooltip
                                title={open ? 'Cerrar' : 'Abrir'}
                            >
                                <IconButton size="large" edge="end" aria-label="delete" onClick={handleChange}>
                                    {open ? <ExpandLess /> : <ExpandMore />}
                                </IconButton>
                            </Tooltip>
                        }
                    </Stack>
                }
            />
            <Divider variant="middle"/>
            <Collapse
                in={open}
                timeout="auto"
                unmountOnExit
            >
                <CardContent>
                    {children}
                </CardContent>
            </Collapse>
        </Card>
    );
}