import { Card, CardHeader, IconButton, Box, Menu, Typography } from '@mui/material';

import InfoIcon from '@mui/icons-material/Info';

import MenuItemComponent from '../MenuItemComponent';
import { global } from '@/styles/global';
import { useMenu } from '@/hooks/useMenu';

export default function CardComponent (props) {
    const { title, subtitle, additional, menu, children } = props;
    const { handleOpenMenu, handleCloseMenu, open, openStatus } = useMenu()

    return (
        <>
            <Card sx={{...global.border, padding: 2}}>
                <Box sx={additional ? {...global.rowFlex, alignItems: "center", justifyContent: 'space-between'} : {}}>
                    <CardHeader
                        title={title}
                        subheader={subtitle}
                        action={menu &&
                            <IconButton
                                sx={{ ml: 2 }}
                                onClick={handleOpenMenu}
                            >
                                <InfoIcon />
                            </IconButton>
                        }
                    />
                    {additional && 
                        <Typography variant="subtitle1" sx={{paddingRight: 2}}>{additional}</Typography>
                    }  
                </Box>
                {children}
            </Card>
            {menu &&
                <Menu 
                    anchorEl={open}
                    open={openStatus}
                    onClose={handleCloseMenu}
                    onClick={handleCloseMenu}
                    transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                    anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                >
                    {menu.map((item) => (
                        <MenuItemComponent
                            id={item.name}
                            name={item.name}
                            Icon={item.Icon}
                            handleClickMenu={item.handleClickMenu}
                        />
                    ))}
                </Menu>
            }
        </>
    );
}