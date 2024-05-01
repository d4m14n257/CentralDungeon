import { Card, CardHeader, IconButton, Box, Typography, CardContent } from '@mui/material';

import { global } from '@/styles/global';

export default function CardComponent (props) {
    const { title, subtitle, additional, Actions, children, handleAction, data } = props;

    return (
        <Card sx={{...global.border, padding: 2}}>
            <Box sx={additional ? {...global.rowFlex, alignItems: "center", justifyContent: 'space-between'} : {}}>
                <CardHeader
                    title={title}
                    subheader={subtitle}
                    action={Actions && 
                        <Actions 
                            handleOpenModal={handleAction}
                            data={data}
                        />
                    }
                />
                {additional && 
                    <Typography variant="subtitle1" sx={{paddingRight: 2}}>{additional}</Typography>
                }  
            </Box>
            <CardContent>
                {children}
            </CardContent>
        </Card>
    );
}