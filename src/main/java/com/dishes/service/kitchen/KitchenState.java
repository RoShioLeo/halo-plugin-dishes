package com.dishes.service.kitchen;



import com.dishes.extension.MealTicketState;
import java.util.List;

public record KitchenState(MealTicketState ticket, boolean existing, List<MealSource> contributions,
                           MealMenu menu) {}
